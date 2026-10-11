package com.nocteon.nocteon_api.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import com.nocteon.nocteon_api.ai.controller.AiAdminController;
import com.nocteon.nocteon_api.ai.service.AiSearchService;
import com.nocteon.nocteon_api.ai.service.ProductIndexingService;

/**
 * Whether the {@code ai} module's beans can be <em>built</em>, as opposed to whether their
 * collaborators behave correctly.
 *
 * <h2>Why this test exists</h2>
 * {@code EmbeddingModelFingerprint} has two public constructors: one taking an
 * {@code Environment}, which is the Spring path, and one taking
 * {@code (String, String, int)}, which exists so callers outside a container can assert a
 * specific fingerprint. With more than one declared constructor and no {@code @Autowired} on
 * either, Spring declines to choose between them and falls back to looking for a no-arg
 * constructor, which the class does not have. The application refused to boot with
 * {@code BeanInstantiationException: No default constructor found} — found by starting the
 * application, and by nothing else.
 *
 * <p>Every other test in this package missed it for two compounding reasons.
 * {@code ApplicationContextRunner} takes an <em>explicit</em> list of configuration classes,
 * which bypasses component scanning, so no test ever asked "can the container construct this
 * class by scanning it?" — a class with two constructors is perfectly fine in a hand-written
 * test. And the unit tests call {@code new EmbeddingModelFingerprint(...)} directly, so they
 * exercise the second constructor and never the first. The class was correct and the wiring was
 * not, and only the wiring was broken.
 *
 * <h2>What is asserted, and why it is split in two</h2>
 * Instantiating every stereotype in the {@code ai} package in isolation is not possible: the
 * services need repositories, the repositories need a {@code DataSource}, and the controllers
 * need their services. Standing all of that up would make this a full integration test of a
 * package that has no integration test, and every unrelated failure would have to be triaged
 * before the signal could be read.
 *
 * <p>So the coverage is split.
 *
 * <p>{@link #scanningTheConfigPackageBuildsEveryBeanInIt()} boots a real
 * {@code @ComponentScan} over the one package in the module with nothing to satisfy, so the
 * failure reproduces exactly as it did in production, message and all.
 *
 * <p>{@link #everyComponentInTheAiModuleOffersExactlyOneConstructorSpringCanChoose()} asks
 * Spring's <em>own</em> constructor-resolution logic — the same
 * {@code AutowiredAnnotationBeanPostProcessor} the container runs — the same question, for
 * every stereotype the scanner finds anywhere in the module, without needing a single one of
 * them to instantiate. That second test is the one that catches this whole class of defect the
 * next time it appears, anywhere in the module, rather than only this instance of it.
 */
class ComponentScanWiringTest {

    /**
     * A real component scan over {@code com.nocteon.nocteon_api.ai.config}.
     *
     * <p>An explicit list of classes would defeat the purpose — that is the harness this module's
     * other wiring tests use, and it is precisely what hid the bug.
     */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = EmbeddingModelFingerprint.class)
    static class ScanTheConfigPackage {
    }

    /**
     * Exposes {@code determineCandidateConstructors}, the protected method that decides which
     * constructors a bean class offers.
     *
     * <p>This is the whole defect in one call, and it is the container's own logic rather than a
     * reimplementation of it. Asked of {@code EmbeddingModelFingerprint} with two unannotated
     * constructors it returns {@code null} — the "declined to choose" answer, which the container
     * then turns into a lookup for a no-arg constructor and, finding none, into a boot failure.
     * Asked of the fixed class it returns the one annotated constructor. Asked of a class with a
     * single constructor it returns that constructor, annotated or not, which is Spring's
     * long-standing "only one, so there is nothing to choose" rule.
     *
     * <p>Not a mock and not a stub: the production class, used for its production job, so the
     * test cannot drift away from what the container actually does.
     */
    private static final class ConstructorResolver extends AutowiredAnnotationBeanPostProcessor {

        Constructor<?>[] candidatesFor(Class<?> beanClass) {
            return determineCandidateConstructors(beanClass, beanClass.getName());
        }
    }

    private static final ConstructorResolver CONSTRUCTORS = new ConstructorResolver();

    /** The base package {@code ai} is, written out so a package move fails here and not silently. */
    private static final String AI_MODULE = "com.nocteon.nocteon_api.ai";

    private static Set<Class<?>> aiModuleComponents;

    @BeforeAll
    static void scanTheModule() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        aiModuleComponents = scanner.findCandidateComponents(AI_MODULE)
                .stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(name -> name != null)
                .map(name -> ClassUtils.resolveClassName(name, ComponentScanWiringTest.class.getClassLoader()))
                .sorted(Comparator.comparing(Class::getName))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Spring's answer to "how would you construct this class", reduced to the two facts the
     * assertion needs.
     *
     * @param type               the scanned class
     * @param declaredCount      how many constructors the class declares
     * @param selected           how many Spring chose between, {@code 0} for the "declined" answer
     * @param noArgAvailable     whether a no-arg constructor exists to fall back to
     */
    private record Resolution(
            Class<?> type, int declaredCount, int selected, boolean noArgAvailable) {

        /**
         * Whether the container can construct this class without guessing.
         *
         * <p>Spring has exactly two legitimate routes: use the one constructor it was offered,
         * or fall through to a no-arg one. "Offered nothing and has no no-arg constructor to
         * fall through to" is the third possibility, and it is a boot failure.
         */
        boolean constructible() {
            return selected == 1 || (selected == 0 && noArgAvailable);
        }

        String describe() {
            return type.getName()
                    + " declares " + declaredCount + " constructor(s); Spring selected " + selected
                    + (noArgAvailable ? ", and a no-arg constructor is available" : ", and no no-arg constructor exists")
                    + ". Several constructors and no @Autowired is not a test-only convenience: the"
                    + " container stops choosing, looks for a no-arg constructor, finds none, and the"
                    + " application refuses to boot with 'No default constructor found'.";
        }
    }

    private static Resolution resolve(Class<?> type) {
        Constructor<?>[] candidates = CONSTRUCTORS.candidatesFor(type);
        Constructor<?>[] declared = type.getDeclaredConstructors();

        return new Resolution(type, declared.length, candidates == null ? 0 : candidates.length,
                Arrays.stream(declared).anyMatch(ctor -> ctor.getParameterCount() == 0));
    }

    /**
     * The anti-vacuity guard, and it comes first on purpose.
     *
     * <p>A scan-based test that silently finds nothing passes forever while asserting nothing,
     * which is how a whole module can end up with no coverage for this class of defect and no
     * sign of it. So the discovered set is pinned to classes that certainly exist and to a floor
     * on its size: a rename or a package move breaks this loudly instead of quietly turning the
     * tests below into no-ops.
     */
    @Test
    void theModuleScanFindsTheModuleRatherThanNothing() {
        assertThat(aiModuleComponents)
                .describedAs("the classpath scan must actually find the ai module's stereotypes")
                .contains(
                        EmbeddingModelFingerprint.class,
                        AiAdminController.class,
                        AiSearchService.class,
                        ProductIndexingService.class)
                .hasSizeGreaterThanOrEqualTo(10);
    }

    /**
     * The reproduction. A real scan over the package, a real refresh, a real bean.
     *
     * <p>Only {@code ai.config} is scanned. It holds the fingerprint and one
     * {@code @Configuration} registering a properties bean, so nothing here needs a database —
     * which is what lets a genuine {@code @ComponentScan} run without becoming a full integration
     * test. The assertion is on the value, not merely on survival: the bean proves the container
     * chose the {@code Environment} constructor, and the value proves it chose it for the reason
     * intended rather than by accident.
     */
    @Test
    void scanningTheConfigPackageBuildsEveryBeanInIt() {
        new ApplicationContextRunner()
                .withUserConfiguration(ScanTheConfigPackage.class)
                .withPropertyValues(
                        EmbeddingProviderEnvironmentPostProcessor.TEXT_EMBEDDING_SELECTOR_PROPERTY
                                + "=" + EmbeddingProviderEnvironmentPostProcessor.GOOGLE_GENAI_PROVIDER,
                        EmbeddingProviderEnvironmentPostProcessor.EMBEDDING_SELECTOR_PROPERTY
                                + "=" + EmbeddingProviderEnvironmentPostProcessor.NO_PROVIDER,
                        "spring.ai.google.genai.embedding.text.model=gemini-embedding-2",
                        "spring.ai.vectorstore.pgvector.dimensions=768")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    assertThat(context).hasSingleBean(EmbeddingModelFingerprint.class);
                    assertThat(context.getBean(EmbeddingModelFingerprint.class).value())
                            .isEqualTo("google-genai:gemini-embedding-2:768");
                });
    }

    /**
     * The whole module, and the class of defect rather than this instance of it.
     *
     * <p>Every stereotype the scanner finds must be constructible. {@code ai} is the only package
     * this branch adds, so this is every new component in the change — a future ambiguous
     * constructor anywhere under it is caught here whether or not anyone remembers to look.
     */
    @Test
    void everyComponentInTheAiModuleOffersExactlyOneConstructorSpringCanChoose() {
        assertThat(aiModuleComponents)
                .describedAs("guarding against a vacuous pass over an empty scan")
                .isNotEmpty();

        for (Class<?> component : aiModuleComponents) {
            Resolution resolution = resolve(component);

            assertThat(resolution.constructible())
                    .describedAs(resolution::describe)
                    .isTrue();
        }
    }

    /**
     * The bug itself, pinned at the instance that shipped it.
     *
     * <p>Kept separate from the loop because that loop reports one failure among many, whereas
     * this says plainly that the fingerprint — the class that actually broke a boot — offers one
     * constructor, that the constructor is the {@code Environment} one, and that it is the one
     * marked {@code @Autowired}. If someone drops the annotation while keeping the second
     * constructor, this names the contract that broke.
     *
     * <p>It also pins the other direction: the direct constructor has to survive. The unit
     * tests and any caller asserting a specific fingerprint still use it, and closing this bug
     * is not licence to remove it — the annotation exists so Spring can choose, not so the
     * alternative could go.
     */
    @Test
    void theFingerprintOffersOneConstructorAndItIsTheAnnotatedEnvironmentOne() {
        Resolution resolution = resolve(EmbeddingModelFingerprint.class);

        assertThat(resolution.selected()).isEqualTo(1);
        assertThat(CONSTRUCTORS.candidatesFor(EmbeddingModelFingerprint.class)[0].getParameterTypes())
                .containsExactly(Environment.class);

        assertThat(EmbeddingModelFingerprint.class.getDeclaredConstructors())
                .describedAs("the direct constructor is for non-Spring callers and must remain")
                .hasSize(2);

        assertThat(EmbeddingModelFingerprint.class.getConstructors())
                .describedAs("the constructor the container is told to use has to be the annotated one")
                .anySatisfy(constructor ->
                        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue());
    }
}