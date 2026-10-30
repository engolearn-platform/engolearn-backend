package fit.iuh.engolearn;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import fit.iuh.engolearn.shared.web.ApiResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "fit.iuh.engolearn", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT_PACKAGE = "fit.iuh.engolearn";
    private static final String BASE = ROOT_PACKAGE + ".";
    private static final Set<String> INFRA = Set.of("shared", "config");
    private static final Set<String> TOP_LEVEL_PACKAGES = Set.of(
        "topic", "dictionary", "user", "learningpath", "subscription", "quota", "payment", "shared", "config"
    );
    private static final Set<String> FEATURE_LAYERS = Set.of(
        "contract", "controller", "dto", "service", "repository", "model", "exception", "utils", "mapper", "infra"
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

                if (!INFRA.contains(root) && (parts.length < 2 || !FEATURE_LAYERS.contains(parts[1]))) {
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

    @ArchTest
    static final ArchRule controllers_expose_the_api_envelope =
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            .should().haveRawReturnType(ApiResponse.class);

    @ArchTest
    static final ArchRule per_controller_advice_lives_with_its_controllers =
        classes().that().areAnnotatedWith(RestControllerAdvice.class)
            .should(new ArchCondition<JavaClass>(
                "reside in each target controller's package or its exact exceptionhandler subpackage"
            ) {
                @Override
                public void check(JavaClass adviceClass, ConditionEvents events) {
                    for (Class<?> controller : assignableControllers(adviceClass)) {
                        String controllerPackage = controller.getPackageName();
                        String advicePackage = adviceClass.getPackageName();
                        if (!advicePackage.equals(controllerPackage)
                                && !advicePackage.equals(controllerPackage + ".exceptionhandler")) {
                            events.add(SimpleConditionEvent.violated(adviceClass,
                                adviceClass.getName() + " resides in " + adviceClass.getPackageName()
                                    + " but targets " + controller.getName()
                                    + " in " + controller.getPackageName()));
                        }
                    }
                }
            });

    @ArchTest
    static final ArchRule per_controller_advice_returns_problem_detail =
        methods().that().areAnnotatedWith(ExceptionHandler.class)
            .should(new ArchCondition<JavaMethod>("return ProblemDetail in per-controller advice") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    if (assignableControllers(method.getOwner()).length == 0) return;
                    if (!method.getRawReturnType().isEquivalentTo(ProblemDetail.class)) {
                        events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " returns " + method.getRawReturnType().getName()
                                + " instead of " + ProblemDetail.class.getName()));
                    }
                }
            });

    // assignableTypes is a Class[], which ArchUnit's annotation API cannot express, so read it reflectively.
    private static Class<?>[] assignableControllers(JavaClass adviceClass) {
        RestControllerAdvice advice = adviceClass.reflect().getAnnotation(RestControllerAdvice.class);
        return advice == null ? new Class<?>[0] : advice.assignableTypes();
    }

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
