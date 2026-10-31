package fit.iuh.engolearn.assessment.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.databind.ObjectMapper;

@ControllerAdvice(assignableTypes=AssessmentController.class)
@RequiredArgsConstructor
public class AssessmentRequestBodyAdvice extends RequestBodyAdviceAdapter {
    private final ObjectMapper mapper;

    @Override public boolean supports(MethodParameter parameter,Type target,Class<? extends HttpMessageConverter<?>> converter) {
        return target instanceof Class<?> type && type.isRecord()
                && type.getPackageName().equals("fit.iuh.engolearn.assessment.dto.request");
    }

    @Override public HttpInputMessage beforeBodyRead(HttpInputMessage input,MethodParameter parameter,Type target,
            Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        var bytes=input.getBody().readNBytes(4097);
        if(bytes.length>4096) throw invalid(input);
        tools.jackson.databind.JsonNode node;
        try {node=mapper.readTree(bytes);}
        catch(RuntimeException malformed) {throw invalid(input);}
        var fields=((Class<?>)target).getRecordComponents();
        if(node==null||!node.isObject()||node.size()!=fields.length) throw invalid(input);
        for(var field:fields) {
            var value=node.get(field.getName());var type=field.getType();
            if(value==null) throw invalid(input);
            if(value.isNull() && field.getName().equals("choice")) continue;
            boolean valid=type==String.class?value.isString():type==boolean.class?value.isBoolean():
                    type==long.class?value.isIntegralNumber()&&value.canConvertToLong():value.isIntegralNumber()&&value.canConvertToInt();
            if(!valid) throw invalid(input);
        }
        return new HttpInputMessage() {
            public InputStream getBody() {return new ByteArrayInputStream(bytes);}
            public HttpHeaders getHeaders() {return input.getHeaders();}
        };
    }
    private HttpMessageNotReadableException invalid(HttpInputMessage input) {
        return new HttpMessageNotReadableException("Assessment request fields or types are invalid",input);
    }
}
