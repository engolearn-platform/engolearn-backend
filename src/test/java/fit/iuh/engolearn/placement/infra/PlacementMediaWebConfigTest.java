package fit.iuh.engolearn.placement.infra;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlacementMediaWebConfigTest {
    @TempDir Path directory;
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    byte[] wav;
    String url;
    @Configuration @EnableWebMvc static class MvcConfig {}
    @BeforeEach void setup() throws Exception {
        wav=Files.readAllBytes(Path.of("tools/mongo-json-importer/samples/assessment-media/q31.wav"));
        String name=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(wav))+".wav";
        Files.write(directory.resolve(name),wav);
        Files.writeString(directory.resolve("private.txt"),"not media");
        url="/api/v1/placement/media/"+name;
        context=new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                "engolearn.placement.media-directory="+directory.toString().replace('\\','/'));
        context.register(MvcConfig.class,PlacementMediaWebConfig.class);
        context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterEach void close() { if(context!=null) context.close(); }
    @Test void servesOriginalWavAndSupportsHeadAndByteRange() throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(wav))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.equalTo("audio/wav"), org.hamcrest.Matchers.equalTo("audio/x-wav"))));
        mvc.perform(head(url)).andExpect(status().isOk()).andExpect(header().string("Content-Length",String.valueOf(wav.length)))
                .andExpect(content().bytes(new byte[0]));
        mvc.perform(get(url).header("Range","bytes=0-31")).andExpect(status().isPartialContent())
                .andExpect(content().bytes(java.util.Arrays.copyOf(wav,32)));
        mvc.perform(get(url).header("Range","bytes="+wav.length+"-")).andExpect(status().isRequestedRangeNotSatisfiable());
    }
    @Test void deniesNonMediaAndMissingFiles() throws Exception {
        mvc.perform(get("/api/v1/placement/media/private.txt")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/placement/media/"+"0".repeat(64)+".wav")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/placement/media/../private.txt")).andExpect(status().isNotFound());
    }
    @Test void rechecksContainmentAfterAnInitiallyValidFileIsReplacedByASymlink() throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk());
        Path outside=Files.createTempFile("placement-outside-", ".wav");
        try {
            Files.write(outside,wav);
            Path link=directory.resolve(url.substring(url.lastIndexOf('/')+1));
            Files.delete(link);
            try { Files.createSymbolicLink(link,outside); }
            catch (UnsupportedOperationException | java.io.IOException unavailable) {
                Assumptions.abort("OS does not permit test symlinks: "+unavailable.getClass().getSimpleName());
            }
            mvc.perform(get(url)).andExpect(status().isNotFound());
        } finally { Files.deleteIfExists(outside); }
    }

}
