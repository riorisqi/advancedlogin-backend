package com.advancedlogin.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/** Dependencies only point inwards: config → adapter → application → domain. */
@AnalyzeClasses(packages = "com.advancedlogin", importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

    private static final String DOMAIN = "com.advancedlogin.domain..";
    private static final String APPLICATION = "com.advancedlogin.application..";
    private static final String ADAPTER = "com.advancedlogin.adapter..";
    private static final String CONFIG = "com.advancedlogin.config..";

    @ArchTest
    static final ArchRule layers_only_depend_inwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Adapter").definedBy(ADAPTER)
            .layer("Config").definedBy(CONFIG)
            .whereLayer("Config").mayNotBeAccessedByAnyLayer()
            .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Config")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Config")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Config");

    @ArchTest
    static final ArchRule domain_and_application_are_framework_free = noClasses()
            .that().resideInAnyPackage(DOMAIN, APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.servlet..", "io.lettuce..", "tools.jackson..");

    @ArchTest
    static final ArchRule inbound_and_outbound_adapters_are_independent = noClasses()
            .that().resideInAPackage("com.advancedlogin.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("com.advancedlogin.adapter.out..");

    @ArchTest
    static final ArchRule outbound_adapters_do_not_know_inbound = noClasses()
            .that().resideInAPackage("com.advancedlogin.adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("com.advancedlogin.adapter.in..");
}
