package fit.iuh.engolearn.subscription.controller;

import fit.iuh.engolearn.subscription.dto.request.AdminPublicationRequest;
import fit.iuh.engolearn.subscription.dto.request.TrialActivationRequest;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ControllerAdvice(assignableTypes = {AdminConfigurationController.class, SubscriptionController.class})
@RequiredArgsConstructor
public class SubscriptionRequestBodyAdvice extends RequestBodyAdviceAdapter {
    private final ObjectMapper mapper;
    private final HttpServletRequest request;

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return parameter.getContainingClass() == AdminConfigurationController.class && targetType == AdminPublicationRequest.class ||
                parameter.getContainingClass() == SubscriptionController.class && (targetType == TrialActivationRequest.class || targetType == fit.iuh.engolearn.subscription.dto.request.CreatePurchaseRequest.class);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        boolean admin = targetType == AdminPublicationRequest.class;
        if (admin) {
            if (parameter.getMethod().getName().equals("publish"))
                SubscriptionHttpValidation.adminKey(request.getHeader("Idempotency-Key"));
            Map<?, ?> variables = (Map<?, ?>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            SubscriptionHttpValidation.scope((String) variables.get("scope"));
        }
        byte[] bytes = input.getBody().readAllBytes();
        JsonNode node;
        try {
            node = mapper.readTree(bytes);
        } catch (RuntimeException ex) {
            if (admin) throw SubscriptionHttpValidation.invalidAdmin();
            throw new HttpMessageNotReadableException("A valid empty JSON object is required", ex, input);
        }
        if (admin) {
            shape(node, AdminPublicationRequest.class, "");
        } else if (targetType == fit.iuh.engolearn.subscription.dto.request.CreatePurchaseRequest.class) {
            shape(node, fit.iuh.engolearn.subscription.dto.request.CreatePurchaseRequest.class, "");
        } else {
            if (node == null || node.isMissingNode())
                throw new HttpMessageNotReadableException("A valid empty JSON object is required", input);
            if (node != null && !node.isNull() && !node.isObject())
                throw new HttpMessageNotReadableException("A valid empty JSON object is required", input);
            SubscriptionHttpValidation.trialKey(request.getHeader("Idempotency-Key"));
            if (node == null || node.isNull() || node.size() != 0) throw SubscriptionHttpValidation.invalidTrial();
        }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage input, MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        if (targetType == TrialActivationRequest.class) throw SubscriptionHttpValidation.invalidTrial();
        return body;
    }

    private void shape(JsonNode node, Type type, String path) {
        if (node == null) throw shapeError(path, "Field is required");
        if (node.isNull()) return;
        if (type instanceof ParameterizedType list) {
            if (!node.isArray()) throw shapeError(path, "Array is required");
            for (int i = 0; i < node.size(); i++)
                shape(node.get(i), list.getActualTypeArguments()[0], path + "[" + i + "]");
            return;
        }
        Class<?> cls = (Class<?>) type;
        if (cls.isRecord()) {
            if (!node.isObject()) throw shapeError(path, "Object is required");
            Set<String> names = new HashSet<>();
            for (RecordComponent component : cls.getRecordComponents()) {
                names.add(component.getName());
                shape(node.get(component.getName()), component.getGenericType(),
                        path.isEmpty() ? component.getName() : path + "." + component.getName());
            }
            if (node.size() != names.size()) throw shapeError(path, "Unknown fields are forbidden");
        } else if (cls == Long.class || cls == Integer.class) {
            if (!node.isIntegralNumber()) throw shapeError(path, "Integer is required");
        } else if (!node.isString()) {
            throw shapeError(path, "String is required");
        } else if (cls == java.time.Instant.class && !node.asString().endsWith("Z")) {
            throw shapeError(path, "UTC instant is required");
        }
    }

    private SubscriptionRequestException shapeError(String field, String message) {
        return new SubscriptionRequestException(400, "INVALID_REQUEST", "Request fields or values are invalid",
                List.of(new SubscriptionRequestException.FieldError(field, message)));
    }
}
