package fit.iuh.engolearn.payment.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnpayProtocol {
    private VnpayProtocol() {}
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public static String date(Instant value) {return DATE.format(value.atZone(ZONE));}
    public static Instant instant(String value) {return LocalDateTime.parse(value,DATE).atZone(ZONE).toInstant();}
    public static String amount(long vnd) {
        if (vnd<=0 || vnd>9_999_999_999L) throw new IllegalArgumentException("VNPay amount out of range");
        return Long.toString(Math.multiplyExact(vnd,100));
    }
    public static long vnd(String value) {
        if (value==null || !value.matches("[0-9]{1,12}")) throw new IllegalArgumentException("Invalid VNPay amount");
        long amount=Long.parseLong(value);
        if (amount==0 || amount%100!=0) throw new IllegalArgumentException("Invalid VND unit");
        return amount/100;
    }
    public static String query(Map<String,String> fields) {
        List<String> parts=new ArrayList<>();
        new TreeMap<>(fields).forEach((key,value)->{
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType") && value!=null && !value.isEmpty())
                parts.add(encode(key)+"="+encode(value));
        });
        return String.join("&",parts);
    }
    public static String hash(String secret,String data) {
        try {
            Mac mac=Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {throw new IllegalStateException("HMAC unavailable",failure);}
    }
    public static boolean matches(String expected,String received) {
        if (received==null || !received.matches("[a-fA-F0-9]{128}")) return false;
        return MessageDigest.isEqual(HexFormat.of().parseHex(expected),HexFormat.of().parseHex(received));
    }
    public static boolean verifyQuery(String secret,Map<String,String> fields) {
        return matches(hash(secret,query(fields)),fields.get("vnp_SecureHash"));
    }
    public static String pipe(Map<String,String> fields,String... names) {
        return String.join("|",Arrays.stream(names).map(name->fields.getOrDefault("vnp_"+name,"")).toList());
    }
    public static boolean verifyApi(String secret,Map<String,String> fields,boolean refund) {
        String data=pipe(fields,"ResponseId","Command","ResponseCode","Message","TmnCode","TxnRef","Amount","BankCode",
                "PayDate","TransactionNo","TransactionType","TransactionStatus","OrderInfo");
        if (!refund) data+="|"+pipe(fields,"PromotionCode","PromotionAmount");
        return matches(hash(secret,data),fields.get("vnp_SecureHash"));
    }
    public static String attempt(String reference) {
        return UUID.nameUUIDFromBytes(reference.getBytes(StandardCharsets.UTF_8)).toString().replace("-","");
    }
    private static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
