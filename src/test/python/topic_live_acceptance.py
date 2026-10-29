"""Real proxy lifecycle acceptance. Explicit --mutate changes one existing topic only."""
import argparse
import concurrent.futures
import json
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--base', default='http://localhost:3000/api/v1')
    parser.add_argument('--topic', required=True)
    parser.add_argument('--mutate', action='store_true', required=True)
    parser.add_argument('--evidence', default='target/topic-live-evidence.json')
    args = parser.parse_args()
    started = time.monotonic()
    evidence = {'topicId': args.topic, 'startedUtc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()), 'requests': [], 'checks': []}
    output = Path(args.evidence)
    output.parent.mkdir(parents=True, exist_ok=True)

    def save():
        output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')

    def request(method, path, body=None, key=None):
        if time.monotonic() - started > 16 * 60:
            raise RuntimeError('Live harness time cap reached; no automatic retry')
        headers = {'Content-Type': 'application/json'}
        if key:
            headers['Idempotency-Key'] = key
        req = urllib.request.Request(args.base + path, json.dumps(body).encode() if body is not None else None, headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                status, payload = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, payload = error.code, json.load(error)
        evidence['requests'].append({'method': method, 'path': path, 'body': body, 'key': key, 'status': status, 'response': payload})
        return status, payload

    def check(name, condition, detail=None):
        evidence['checks'].append({'name': name, 'passed': bool(condition), 'detail': detail})
        print(('PASS ' if condition else 'FAIL ') + name, flush=True)
        if not condition:
            save()
            raise AssertionError(name + ': ' + str(detail))

    def data(path):
        status, payload = request('GET', path)
        check('GET ' + path, status == 200, {'status': status, 'code': payload.get('code')})
        return payload['data']

    def post(path, body, key=None, expected=200, code=None):
        key = key or ('live-ux-' + uuid.uuid4().hex)
        status, payload = request('POST', path, body, key)
        check('POST ' + path + ' => ' + str(expected), status == expected and (code is None or payload.get('code') == code), {'status': status, 'code': payload.get('code')})
        return payload.get('data', payload), key

    def navigate(attempt_id):
        nav = data('/topic-attempts/' + attempt_id + '/quiz-navigation')
        for entry in nav['entries']:
            if entry['status'] == 'LOCKED':
                check('locked question has metadata only', entry['quiz'] is None and entry['feedback'] is None and entry['firstTryCorrect'] is None)
            if entry['status'] == 'CURRENT':
                check('current question has no answer feedback', entry['quiz'] is not None and entry['feedback'] is None and entry['firstTryCorrect'] is None)
        return nav

    answers = {}

    def finish(attempt_id, lower_score=False):
        current = data('/topic-attempts/' + attempt_id)
        solved_probe = False
        while current['status'] != 'COMPLETED':
            position = current['position']
            if position['stage'] not in ('FILL_BLANK', 'SITUATIONAL_CHOICE'):
                current, _ = post('/topic-attempts/' + attempt_id + '/advance', {'revision': current['revision'], **position})
                continue
            nav = navigate(attempt_id)
            entry = next(v for v in nav['entries'] if v['status'] == 'CURRENT')
            quiz_id = entry['quizId']
            correct = answers[quiz_id]
            if lower_score:
                wrong = next(option['optionId'] for option in entry['quiz']['options'] if option['optionId'] != correct)
                body = {'revision': current['revision'], 'optionId': wrong}
                result, key = post('/topic-attempts/' + attempt_id + '/quizzes/' + quiz_id + '/answers', body)
                check('wrong answer preserves frontier and hides feedback', not result['isCorrect'] and result['feedback'] is None and result['attempt']['position'] == position)
                replay, _ = post('/topic-attempts/' + attempt_id + '/quizzes/' + quiz_id + '/answers', body, key)
                check('wrong answer exact-key replay identical', replay == result)
                current = result['attempt']
            body = {'revision': current['revision'], 'optionId': correct}
            result, key = post('/topic-attempts/' + attempt_id + '/quizzes/' + quiz_id + '/answers', body)
            check('authoritative correct answer accepted', result['isCorrect'] and result['feedback'] is not None)
            replay, _ = post('/topic-attempts/' + attempt_id + '/quizzes/' + quiz_id + '/answers', body, key)
            check('correct answer exact-key replay identical', replay == result)
            current = result['attempt']
            if not solved_probe and current['status'] != 'COMPLETED':
                reached = navigate(attempt_id)
                saved = next(v for v in reached['entries'] if v['quizId'] == quiz_id)
                check('solved running question readable with first-try status', saved['status'] == 'COMPLETED' and saved['feedback'] is not None and saved['firstTryCorrect'] == (not lower_score))
                again = data('/topic-attempts/' + attempt_id)
                check('navigation read does not advance revision/frontier', again['revision'] == current['revision'] and again['position'] == current['position'])
                post('/topic-attempts/' + attempt_id + '/quizzes/' + quiz_id + '/answers', {'revision': current['revision'], 'optionId': correct}, expected=409, code='STATE_CHANGED')
                solved_probe = True
        result = data('/topic-attempts/' + attempt_id + '/result')
        if lower_score:
            check('completed lower score is official', result['firstTryCorrectCount'] == 0, result)
        return result

    try:
        evidence['viewer'] = data('/topic-learning/viewer')
        mine = data('/topics/mine?status=IN_PROGRESS&limit=100')
        other = next(v['topicId'] for v in mine['items'] if v['topicId'] != args.topic)
        baseline_other = data('/topics/' + other + '/progress')
        detail = data('/topics/' + args.topic)
        evidence['before'] = detail
        check('designated topic starts completed and reset eligible', detail['progress']['status'] == 'COMPLETED' and detail['resetAvailability']['allowed'])
        for item in detail['items']:
            review = data('/topic-attempts/' + item['completedAttemptId'] + '/review')
            answers.update({q['quiz']['quizId']: q['feedback']['correctOptionId'] for q in review['quizzes']})
        session = detail.get('retrySession')
        if session:
            first = True
            while session['status'] == 'IN_PROGRESS':
                attempt_id = session['currentAttemptId']
                if attempt_id is None:
                    attempt, _ = post('/topic-items/' + session['nextTopicItemId'] + '/attempts', {'mode': 'RETRY', 'retrySessionId': session['retrySessionId']})
                    attempt_id = attempt['attemptId']
                finish(attempt_id, lower_score=first)
                first = False
                session = data('/topic-retry-sessions/' + session['retrySessionId'])
            check('legacy TOPIC session can finish without replacement', session['status'] == 'COMPLETED')

        detail = data('/topics/' + args.topic)
        item_ids = [v['topicItemId'] for v in detail['items']]
        keys = ['live-ux-' + uuid.uuid4().hex for _ in range(2)]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda pair: request('POST', '/topic-items/' + pair[0] + '/attempts', {'mode': 'RETRY'}, pair[1]), zip(item_ids[:2], keys)))
        check('two concurrent different-item starts publish exactly one winner', sorted(status for status, _ in results) == [200, 409], [status for status, _ in results])
        winner = next(payload['data'] for status, payload in results if status == 200)
        active_id = winner['attemptId']
        active_item = winner['topicItemId']
        other_item = next(v for v in item_ids if v != active_item)
        old_official = next(v['completedAttemptId'] for v in detail['items'] if v['topicItemId'] == active_item)
        resumed, resume_key = post('/topic-items/' + active_item + '/attempts', {'mode': 'RETRY'})
        check('fresh-key same-item retry resumes same attempt', resumed['attemptId'] == active_id)
        repeated, _ = post('/topic-items/' + active_item + '/attempts', {'mode': 'RETRY'}, resume_key)
        check('same-item start exact replay identical', repeated == resumed)
        post('/topic-items/' + active_item + '/attempts', {'mode': 'RETRY', 'retrySessionId': resumed['retrySessionId']}, resume_key, expected=409, code='IDEMPOTENCY_KEY_REUSED')
        post('/topic-items/' + other_item + '/attempts', {'mode': 'RETRY'}, expected=409, code='ITEM_RETRY_IN_PROGRESS')
        post('/topic-items/' + active_item + '/attempts', {'mode': 'RETRY', 'replaceRetrySessionId': resumed['retrySessionId']}, expected=409, code='RETRY_RESTART_NOT_ALLOWED')
        post('/topics/' + args.topic + '/retry-sessions', {}, expected=410, code='TOPIC_RETRY_RETIRED')
        data('/topic-attempts/' + old_official + '/review')
        nav = navigate(active_id)
        check('all quizzes locked before content frontier', all(v['status'] == 'LOCKED' for v in nav['entries']))
        check('single retry session remains active', data('/topics/' + args.topic)['retrySession']['currentAttemptId'] == active_id)
        check('other topic untouched before reset', data('/topics/' + other + '/progress') == baseline_other)

        # Reset while an item retry is active; two same-key deliveries must increment once.
        generation = detail['generation']
        reset_key = 'live-ux-' + uuid.uuid4().hex
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            reset_results = list(pool.map(lambda _: request('POST', '/topics/' + args.topic + '/reset', {'generation': generation}, reset_key), range(2)))
        check('concurrent exact reset delivery returns same one-step generation', all(status == 200 and payload['data']['generation'] == generation + 1 for status, payload in reset_results) and reset_results[0][1]['data'] == reset_results[1][1]['data'])
        clean = data('/topics/' + args.topic)
        progress = data('/topics/' + args.topic + '/progress')
        check('reset clears all effective completion/results/retry', clean['generation'] == generation + 1 and clean['retrySession'] is None and clean['progress']['status'] == 'NOT_STARTED' and all(v['learningStatus'] == 'NOT_STARTED' and v['completedAttemptId'] is None and v['retryAttempt'] is None and v['learningAttempt'] is None for v in clean['items']))
        check('reset learned metrics and recency clear', progress['metrics']['learnedVocabularyCount'] == progress['metrics']['learnedExpressionCount'] == progress['metrics']['completedQuizCount'] == 0 and progress['lastAccessedAt'] is None)
        for suffix in ['', '/content', '/quiz', '/quiz-navigation', '/result', '/review']:
            status, payload = request('GET', '/topic-attempts/' + active_id + suffix)
            check('old retry handle rejected ' + suffix, status == 409 and payload.get('code') == 'TOPIC_RESET')
        for suffix in ['/result', '/review']:
            status, payload = request('GET', '/topic-attempts/' + old_official + suffix)
            check('old official handle rejected ' + suffix, status == 409 and payload.get('code') == 'TOPIC_RESET')
        status, payload = request('GET', '/topic-retry-sessions/' + resumed['retrySessionId'])
        check('old retry session rejected', status == 409 and payload.get('code') == 'TOPIC_RESET')
        post('/topic-items/' + active_item + '/attempts', {'mode': 'RETRY'}, resume_key, expected=409, code='TOPIC_RESET')
        post('/topic-attempts/' + active_id + '/advance', {'revision': resumed['revision'], **resumed['position']}, expected=409, code='TOPIC_RESET')
        post('/topics/' + args.topic + '/reset', {'generation': generation + 1}, reset_key, expected=409, code='IDEMPOTENCY_KEY_REUSED')
        post('/topics/' + args.topic + '/reset', {'generation': generation}, expected=409, code='STATE_CHANGED')
        new_attempt, new_start_key = post('/topic-items/' + item_ids[0] + '/attempts', {'mode': 'LEARN'})
        check('fresh LEARN uses new generation without old unfinished-index block', new_attempt['attemptId'] != active_id and new_attempt['mode'] == 'LEARN' and new_attempt['revision'] == 0)
        replay, _ = post('/topics/' + args.topic + '/reset', {'generation': generation}, reset_key)
        check('late reset replay does not clear newer learning', replay['generation'] == generation + 1 and data('/topic-attempts/' + new_attempt['attemptId'])['attemptId'] == new_attempt['attemptId'])
        navigate(new_attempt['attemptId'])
        check('other topic unchanged after all retry/reset operations', data('/topics/' + other + '/progress') == baseline_other)
        mine_after = data('/topics/mine?status=COMPLETED&limit=100')
        check('reset topic removed from completed membership', all(v['topicId'] != args.topic for v in mine_after['items']))
        evidence['after'] = data('/topics/' + args.topic)
        evidence['newLearningAttemptId'] = new_attempt['attemptId']
        evidence['isolationTopicId'] = other
        evidence['outcome'] = 'PASS'
    except Exception as error:
        evidence['outcome'] = 'FAIL'
        evidence['error'] = str(error)
        raise
    finally:
        evidence['elapsedSeconds'] = round(time.monotonic() - started, 3)
        save()
        print(json.dumps({'outcome': evidence.get('outcome'), 'requests': len(evidence['requests']), 'checks': len(evidence['checks']), 'failedChecks': sum(not v['passed'] for v in evidence['checks']), 'evidence': str(output)}, ensure_ascii=True), flush=True)


if __name__ == '__main__':
    main()
