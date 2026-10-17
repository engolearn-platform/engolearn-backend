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

    private static final String ROOT_PACKAGE = "fit.iuh.engolearn";
    private static final String BASE = ROOT_PACKAGE + ".";
    private static final Set<String> FEATURES = Set.of("topic", "dictionary", "user");
    private static final Set<String> INFRA = Set.of("shared", "config");
    private static final Set<String> TOP_LEVEL_PACKAGES = Set.of(
        "topic", "dictionary", "user", "shared", "config"
    );
    private static final Set<String> FEATURE_LAYERS = Set.of(
        "contract", "controller", "dto", "service", "repository", "model", "exception"
    );

    /** Feature name of a package, or null for the root package, shared and config. */
    private static String featureOf(String pkg) {
        if (!pkg.startsWith(BASE)) return null;
        String first = pkg.substring(BASE.length()).split("\\.")[0];
        return INFRA.contains(first) ? null : first;
    }

    @ArchTest
    static final ArchRule classes_use_approved_feature_first_package_roots =
        classes().should(new ArchCondition<JavaClass>(
            "use an approved top-level package and put feature classes under a layer"
        ) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String pkg = origin.getPackageName();
                if (!pkg.equals(ROOT_PACKAGE) && !pkg.startsWith(BASE)) return;

                String relative = pkg.equals(ROOT_PACKAGE) ? "" : pkg.substring(BASE.length());
                if (relative.isEmpty()) {
                    if (!origin.getSimpleName().equals("EngolearnApplication")) {
                        events.add(SimpleConditionEvent.violated(
                            origin, "Only EngolearnApplication may reside in " + ROOT_PACKAGE
                        ));
                    }
                    return;
                }

                String[] parts = relative.split("\\.");
                String root = parts[0];
                if (!TOP_LEVEL_PACKAGES.contains(root)) {
                    events.add(SimpleConditionEvent.violated(
                        origin, "Unapproved top-level package '" + root + "' for " + origin.getName()
                    ));
                    return;
                }

                if (FEATURES.contains(root) && (parts.length < 2 || !FEATURE_LAYERS.contains(parts[1]))) {
                    events.add(SimpleConditionEvent.violated(
                        origin, "Feature classes must use <feature>.<layer>: " + origin.getName()
                    ));
                }
            }
        });

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

                    String contractPackage = BASE + to + ".contract";
                    if (!targetPkg.equals(contractPackage) && !targetPkg.startsWith(contractPackage + ".")) {
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
    static final ArchRule no_cycles_between_packages =
        slices().matching("fit.iuh.engolearn.(*)..").should().beFreeOfCycles();

    // Extend the allow-list deliberately; package dependencies are checked, not class role/behavior.
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
