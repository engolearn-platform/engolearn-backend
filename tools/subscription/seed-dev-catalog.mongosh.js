// Development fixture only; requires the existing dev-main-manifest baseline.
// docker cp tools/subscription/seed-dev-catalog.mongosh.js mongodb_srv:/tmp/seed-dev-catalog.mongosh.js
// docker exec mongodb_srv mongosh --quiet --file /tmp/seed-dev-catalog.mongosh.js
// Optional APP_DB selects the database; root credentials come from the container environment.
// This explicitly replaces demo display metadata, outside normal immutable policy publication.
(() => {
  const username = process.env.MONGO_INITDB_ROOT_USERNAME;
  const password = process.env.MONGO_INITDB_ROOT_PASSWORD;
  if (Boolean(username) !== Boolean(password)) throw new Error('Both root credential variables must be set together');
  if (username && !db.getSiblingDB('admin').auth(username, password)) throw new Error('MongoDB authentication failed');
  const app = db.getSiblingDB(process.env.APP_DB || 'engolearn_db');
  const policies = app.subscription_quota_policy_versions;
  const plans = app.subscription_plan_versions;
  const states = app.subscription_policy_state;
  const configuration = app.subscription_admin_configuration_state;
  const periods = app.subscription_periods;
  const effectiveAt = ISODate('2026-10-01T00:00:00.000Z');
  const counts = { policies: 0, plans: 0, states: 0, periodDisplays: 0, configurationRevisions: 0 };
  const equal = (left, right) => EJSON.stringify(left, { relaxed: true }) === EJSON.stringify(right, { relaxed: true });
  const ensure = (condition, message) => { if (!condition) throw new Error(message); };
  const rules = (seconds, attempts) => [
    { quotaType: 'SPEAKING_DURATION', displayName: 'Thời lượng luyện nói với AI', limit: Long(seconds), unit: 'SECOND', period: 'DAILY', displayOrder: Int32(0) },
    { quotaType: 'PROFICIENCY_CHECK_ATTEMPT', displayName: 'Đánh giá trình độ', limit: Long(attempts), unit: 'ATTEMPT', period: 'MONTHLY', displayOrder: Int32(1) }
  ];
  const benefits = (minutes, attempts) => [
    { code: 'SPEAKING_DURATION', title: 'Luyện nói với AI mỗi ngày', description: `Duy trì thói quen giao tiếp tiếng Anh với tối đa ${minutes} phút luyện nói cùng AI mỗi ngày. Hạn mức thời lượng được làm mới hằng ngày.`, displayValue: `${minutes} phút/ngày`, sortOrder: Int32(0) },
    { code: 'PROFICIENCY_CHECK_ATTEMPT', title: 'Đánh giá trình độ tiếng Anh', description: `Sử dụng tối đa ${attempts} lượt đánh giá trình độ tiếng Anh mỗi tháng để theo dõi việc học. Hạn mức lượt đánh giá được làm mới hằng tháng.`, displayValue: `${attempts} lượt/tháng`, sortOrder: Int32(1) }
  ];
  const fixtures = [
    { scope: 'FREE', id: 'verification-free-v1', planId: 'verification-free-offer-v1', name: 'Miễn phí', seconds: '3600', attempts: '1', minutes: 60,
      description: 'Bắt đầu thói quen học tiếng Anh mỗi ngày với 60 phút luyện nói cùng AI và 1 lượt đánh giá trình độ mỗi tháng. Gói miễn phí không có phí đăng ký.', order: 0, billing: [] },
    { scope: 'TRIAL', id: 'verification-trial-v1', name: 'Dùng thử Premium', seconds: '3600', attempts: '2', minutes: 60 },
    { scope: 'PREMIUM', id: 'dev-premium-v1', planId: 'dev-premium-offer-v1', name: 'Premium', seconds: '7200', attempts: '5', minutes: 120,
      description: 'Dành thêm thời gian cho mục tiêu tiếng Anh với 120 phút luyện nói cùng AI mỗi ngày và 5 lượt đánh giá trình độ mỗi tháng. Chọn thanh toán theo tháng hoặc theo năm phù hợp với kế hoạch học tập.', order: 1,
      billing: [{ billingPeriod: 'MONTHLY', amount: Long('199000'), currency: 'VND' }, { billingPeriod: 'ANNUAL', amount: Long('1590000'), currency: 'VND' }] }
  ];
  const global = configuration.findOne({ _id: 'GLOBAL' });
  ensure(global && global.revision != null, 'Existing GLOBAL configuration state is required');
  const edits = [];
  for (const fixture of fixtures) {
    const expectedRules = rules(fixture.seconds, fixture.attempts);
    const display = benefits(fixture.minutes, fixture.attempts);
    const current = policies.findOne({ _id: fixture.id });
    const state = states.findOne({ _id: fixture.scope });
    if (fixture.scope !== 'PREMIUM') {
      ensure(current && state, `Existing ${fixture.scope} baseline is required`);
      ensure(state.activeVersionId === fixture.id && state.pendingVersionId == null, `${fixture.scope} state differs from the demo baseline`);
    } else if (state) {
      ensure(state.activeVersionId === fixture.id && state.pendingVersionId == null, 'PREMIUM state conflicts with this fixture');
    }
    ensure(!policies.findOne({ scope: fixture.scope, _id: { $ne: fixture.id } }), `Additional ${fixture.scope} policies exist; inspect before reseeding`);
    if (current) {
      ensure(current.scope === fixture.scope && Number(current.version) === 1 && equal(current.effectiveAt, effectiveAt), `Policy identity conflicts: ${fixture.id}`);
      ensure(current.snapshot?.policySnapshotId === fixture.id && current.snapshot.policyScope === fixture.scope && Number(current.snapshot.policyVersion) === 1, `Snapshot identity conflicts: ${fixture.id}`);
      ensure(current.snapshot.rules?.length === 2 && expectedRules.every(expected => current.snapshot.rules.some(actual => actual.quotaType === expected.quotaType && String(actual.limit) === String(expected.limit) && actual.unit === expected.unit && actual.period === expected.period)), `Quota rules conflict: ${fixture.id}`);
      ensure(current.benefits?.policySnapshotId === fixture.id && current.benefits.planCode === (fixture.scope === 'FREE' ? 'FREE' : 'PREMIUM'), `Benefit identity conflicts: ${fixture.id}`);
    }
    const desiredBenefits = { planCode: fixture.scope === 'FREE' ? 'FREE' : 'PREMIUM', displayName: fixture.name, policySnapshotId: fixture.id, benefits: display };
    if (!current) {
      edits.push(() => { policies.insertOne({ _id: fixture.id, scope: fixture.scope, version: Long('1'), effectiveAt,
        snapshot: { policySnapshotId: fixture.id, policyScope: fixture.scope, policyVersion: Long('1'), rules: expectedRules }, benefits: desiredBenefits }); counts.policies++; });
    } else if (!equal(current.benefits, desiredBenefits)) {
      edits.push(() => { counts.policies += policies.updateOne({ _id: fixture.id }, { $set: { benefits: desiredBenefits } }).modifiedCount; });
    }
    if (fixture.planId) {
      ensure(!plans.findOne({ scope: fixture.scope, _id: { $ne: fixture.planId } }), `Additional ${fixture.scope} plans exist; inspect before reseeding`);
      const plan = plans.findOne({ _id: fixture.planId });
      if (plan) ensure(plan.scope === fixture.scope && Number(plan.version) === 1 && equal(plan.effectiveAt, effectiveAt) && plan.published === true, `Plan identity conflicts: ${fixture.planId}`);
      const desiredPlan = { planCode: fixture.scope, displayName: fixture.name, description: fixture.description, benefits: display, billingOptions: fixture.billing };
      if (!plan) {
        edits.push(() => { plans.insertOne({ _id: fixture.planId, scope: fixture.scope, version: Long('1'), effectiveAt, published: true, displayOrder: Int32(fixture.order), plan: desiredPlan }); counts.plans++; });
      } else if (!equal(plan.plan, desiredPlan) || Number(plan.displayOrder) !== fixture.order) {
        edits.push(() => { counts.plans += plans.updateOne({ _id: fixture.planId }, { $set: { plan: desiredPlan, displayOrder: Int32(fixture.order) } }).modifiedCount; });
      }
    }
    if (fixture.scope === 'PREMIUM' && !state) {
      edits.push(() => { states.insertOne({ _id: 'PREMIUM', activeVersionId: fixture.id, activationSequence: Long('1'), revision: Long('0'), manifestFingerprint: 'dev-catalog-v1' }); counts.states++; });
    }
  }
  ensure(!plans.findOne({ scope: 'TRIAL', published: true }), 'A public TRIAL plan exists; inspect before reseeding');
  const trial = fixtures.find(fixture => fixture.scope === 'TRIAL');
  const demoPeriods = periods.find({ userId: '68f8f3a9b9e4a12345678903', kind: 'TRIAL', 'snapshot.policySnapshotId': trial.id }).toArray();
  for (const period of demoPeriods) {
    ensure(period.state === 'ACTIVE' && equal(period.periodStartAt, ISODate('2026-10-30T00:54:55.372Z')) && equal(period.periodEndAt, ISODate('2026-11-06T00:54:55.372Z')), 'Demo Trial dates or state differ; inspect before reseeding');
    ensure(period.benefits?.policySnapshotId === trial.id && period.benefits.planCode === 'PREMIUM', 'Demo Trial benefit identity conflicts');
    const desired = benefits(trial.minutes, trial.attempts);
    ensure(period.benefits.benefits?.length === desired.length && desired.every(item => period.benefits.benefits.some(existing => existing.code === item.code)), 'Demo Trial benefit codes conflict');
    const fields = { 'benefits.displayName': trial.name };
    period.benefits.benefits.forEach((existing, index) => {
      const item = desired.find(candidate => candidate.code === existing.code);
      for (const field of ['title', 'description', 'displayValue']) fields[`benefits.benefits.${index}.${field}`] = item[field];
    });
    if (period.benefits.displayName !== trial.name || period.benefits.benefits.some(existing => {
      const item = desired.find(candidate => candidate.code === existing.code);
      return ['title', 'description', 'displayValue'].some(field => existing[field] !== item[field]);
    })) edits.push(() => { counts.periodDisplays += periods.updateOne({ _id: period._id }, { $set: fields }).modifiedCount; });
  }
  if (edits.length) {
    // Invalidate open admin editors before fixture changes; reruns with identical content do nothing.
    const advanced = configuration.updateOne({ _id: 'GLOBAL', revision: global.revision }, { $inc: { revision: Long('1') } });
    ensure(advanced.modifiedCount === 1, 'GLOBAL revision changed concurrently; reload before seeding');
    counts.configurationRevisions++;
    edits.forEach(edit => edit());
  }
  print(JSON.stringify(counts));
})();
