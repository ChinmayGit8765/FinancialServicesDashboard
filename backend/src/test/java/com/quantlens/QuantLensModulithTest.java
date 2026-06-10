package com.quantlens;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Modulith architecture verification + documentation (Phase 10, SC-3).
 * <p>
 * {@link #applicationModulesShouldBeValid()} is the LIVING module-boundary contract: it verifies
 * that the declared package boundaries form a valid modular structure (no illegal cross-module
 * references, no cycles) — the dependency graph (ai → portfolio + analytics, analytics → portfolio,
 * mcp → portfolio + analytics, auth + config standalone) is enforced on every build.
 * <p>
 * {@link #generatesModuleDocumentation()} renders the module dependency diagram as a generated
 * artifact. This is a plain JUnit class — NO {@code @SpringBootTest}, NO Spring context, NO
 * database. {@link ApplicationModules} uses classpath scanning, and {@link Documenter} emits
 * PlantUML source headlessly (no Graphviz/PlantUML binary required).
 * <p>
 * Only {@code writeModulesAsPlantUml()} is invoked — the per-module {@code writeModuleCanvases()}
 * step parses {@code spring-configuration-metadata.json} to tabulate configuration properties and
 * NPEs on a null metadata array (a known Spring Modulith 1.4.x canvas-generation issue); the
 * PlantUML component diagram is the SC-3 deliverable and is generated independently of canvases.
 */
class QuantLensModulithTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(QuantLensApplication.class);

    @Test
    void applicationModulesShouldBeValid() {
        MODULES.verify();
    }

    @Test
    void generatesModuleDocumentation() {
        // Writes the PlantUML component diagram to target/spring-modulith-docs/ (git-ignored under
        // **/target/). The diagram is a build artifact, not a committed file. Canvas generation is
        // intentionally omitted (see Javadoc). Assert the artifact is actually produced (IN-01).
        new Documenter(MODULES)
                .writeModulesAsPlantUml();

        assertThat(Path.of("target/spring-modulith-docs/components.puml"))
                .as("Documenter must generate the module component diagram")
                .exists();
    }
}
