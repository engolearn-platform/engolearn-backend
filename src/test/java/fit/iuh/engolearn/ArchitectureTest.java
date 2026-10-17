package fit.iuh.engolearn;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "fit.iuh.engolearn", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "fit.iuh.engolearn.";
    private static final Set<String> INFRA = Set.of("shared", "config");

    /** Feature name of a package, or null for the root package, shared and config. */
    private static String featureOf(String pkg) {
        if (!pkg.startsWith(BASE)) return null;
        String first = pkg.substring(BASE.length()).split("\\.")[0];
        return INFRA.contains(first) ? null : first;
    }

    @ArchTest
    static final ArchRule features_use_each_other_only_via_contract =
        classes().should(new ArchCondition<JavaClass>("use other features only through their contract package") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = featureOf(origin.getPackageName());
                if (from == null) return;
                for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                    String targetPkg = d.getTargetClass().getPackageName();
                    String to = featureOf(targetPkg);
                    if (to == null || to.equals(from)) continue;
                    if (!targetPkg.startsWith(BASE + to + ".contract")) {
                        events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                    }
                }
            }
        });

    @ArchTest
    static final ArchRule shared_does_not_depend_on_features =
        classes().that().resideInAPackage("fit.iuh.engolearn.shared..")
            .should(new ArchCondition<JavaClass>("not depend on any feature") {
                @Override
                public void check(JavaClass origin, ConditionEvents events) {
                    for (Dependency d : origin.getDirectDependenciesFromSelf()) {
                        if (featureOf(d.getTargetClass().getPackageName()) != null) {
                            events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                        }
                    }
                }
            });

    @ArchTest
    static final ArchRule no_cycles_between_features =
        slices().matching("fit.iuh.engolearn.(*)..").should().beFreeOfCycles();

    // Extend the allow-list deliberately; contract must stay free of entities and logic layers.
    @ArchTest
    static final ArchRule contract_is_self_contained =
        classes().that().resideInAPackage("..contract..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                "..contract..", "fit.iuh.engolearn.shared..", "java..", "jakarta.validation..", "lombok..");

    @ArchTest
    static final ArchRule layers_inside_features =
        layeredArchitecture().consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Repository").definedBy("..repository..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
            .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service");
}