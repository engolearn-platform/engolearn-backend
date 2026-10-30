package fit.iuh.engolearn.subscription.controller;

import fit.iuh.engolearn.shared.security.CurrentUserProvider;
import fit.iuh.engolearn.shared.web.ApiResponse;
import fit.iuh.engolearn.subscription.dto.request.AdminPublicationInput;
import fit.iuh.engolearn.subscription.dto.response.AdminConfiguration;
import fit.iuh.engolearn.subscription.dto.response.AdminConfiguration.*;
import fit.iuh.engolearn.subscription.exception.SubscriptionRequestException;
import fit.iuh.engolearn.subscription.service.AdminConfigurationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validator;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import java.lang.reflect.*;

@RestController
@RequestMapping("/api/v1/subscription/admin")
public class AdminConfigurationController {
    private final AdminConfigurationService service;private final CurrentUserProvider users;private final ObjectMapper mapper;private final Validator validator;
    public AdminConfigurationController(AdminConfigurationService service,CurrentUserProvider users,ObjectMapper mapper,Validator validator){this.service=service;this.users=users;this.mapper=mapper;this.validator=validator;}
    private void noStore(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
    private SubscriptionRequestException invalid(){return new SubscriptionRequestException(400,"INVALID_REQUEST","Request fields or values are invalid");}
    private String scope(String value){if(!Set.of("FREE","TRIAL","PREMIUM").contains(value))throw invalid();return value;}
    private long positive(String value){try{long number=Long.parseLong(value);if(number<1||number>9_007_199_254_740_991L)throw invalid();return number;}catch(NumberFormatException ex){throw invalid();}}
    private AdminPublicationInput input(String body){
        try {
            shape(mapper.readTree(body),AdminPublicationInput.class,"");
            AdminPublicationInput input=mapper.readerFor(AdminPublicationInput.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).readValue(body);
            if(input==null)throw invalid();
            var violations=validator.validate(input);
            if(!violations.isEmpty())throw new SubscriptionRequestException(400,"INVALID_REQUEST","Request fields or values are invalid",violations.stream().map(v->new SubscriptionRequestException.FieldError(v.getPropertyPath().toString().replace(".<list element>",""),v.getMessage())).toList());
            return input;
        }catch(SubscriptionRequestException ex){throw ex;}catch(RuntimeException ex){throw invalid();}
    }
    private void shape(JsonNode node,Type type,String path){
        if(node==null)throw shapeError(path,"Field is required");
        if(node.isNull())return;
        if(type instanceof ParameterizedType list){
            if(!node.isArray())throw shapeError(path,"Array is required");
            for(int i=0;i<node.size();i++)shape(node.get(i),list.getActualTypeArguments()[0],path+"["+i+"]");
            return;
        }
        Class<?> cls=(Class<?>)type;
        if(cls.isRecord()){
            if(!node.isObject())throw shapeError(path,"Object is required");
            Set<String> names=new HashSet<>();
            for(RecordComponent component:cls.getRecordComponents()){
                names.add(component.getName());shape(node.get(component.getName()),component.getGenericType(),path.isEmpty()?component.getName():path+"."+component.getName());
            }
            if(node.size()!=names.size())throw shapeError(path,"Unknown fields are forbidden");
        }else if(cls==Long.class||cls==Integer.class){if(!node.isIntegralNumber())throw shapeError(path,"Integer is required");}
        else if(!node.isString())throw shapeError(path,"String is required");
        else if(cls==java.time.Instant.class&&!node.asString().endsWith("Z"))throw shapeError(path,"UTC instant is required");
    }
    private SubscriptionRequestException shapeError(String field,String message){return new SubscriptionRequestException(400,"INVALID_REQUEST","Request fields or values are invalid",List.of(new SubscriptionRequestException.FieldError(field,message)));}
    @GetMapping("/catalog")
    public ApiResponse<Catalog> catalog(HttpServletResponse response){noStore(response);return ApiResponse.ok(service.catalog());}
    @GetMapping("/configurations/{scope}")
    public ApiResponse<AdminConfiguration> configuration(@PathVariable String scope,HttpServletResponse response){noStore(response);return ApiResponse.ok(service.configuration(scope(scope)));}
    @GetMapping("/configurations/{scope}/versions")
    public ApiResponse<History> history(@PathVariable String scope,@RequestParam(defaultValue="20") String limit,@RequestParam(required=false) String beforeVersion,HttpServletResponse response){
        noStore(response);long count=positive(limit);if(count>50)throw invalid();return ApiResponse.ok(service.history(scope(scope),(int)count,beforeVersion==null?null:positive(beforeVersion)));
    }
    @GetMapping("/configurations/{scope}/versions/{version}")
    public ApiResponse<Version> detail(@PathVariable String scope,@PathVariable String version,HttpServletResponse response){noStore(response);return ApiResponse.ok(service.detail(scope(scope),positive(version)));}
    @PostMapping("/configurations/{scope}/preview")
    public ApiResponse<Preview> preview(@PathVariable String scope,@RequestBody String body,HttpServletResponse response){noStore(response);return ApiResponse.ok(service.preview(scope(scope),input(body)));}
    @PostMapping("/configurations/{scope}/publications")
    public ApiResponse<PublicationResult> publish(@PathVariable String scope,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestBody String body,HttpServletResponse response){
        noStore(response);try{if(key==null||!UUID.fromString(key).toString().equalsIgnoreCase(key))throw invalid();}catch(IllegalArgumentException ex){throw invalid();}
        var result=service.publish(users.requireCurrentUserId(),key.toLowerCase(Locale.ROOT),scope(scope),input(body));response.setStatus(result.created()?201:200);return ApiResponse.ok(result.result());
    }
}
