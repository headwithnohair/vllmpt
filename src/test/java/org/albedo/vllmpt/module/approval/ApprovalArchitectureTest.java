package org.albedo.vllmpt.module.approval;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 审批模块分层约束：
 * <ul>
 *   <li>core 不依赖具体业务模块 biz；</li>
 *   <li>core / biz 不反向依赖 chat 对话模块。</li>
 * </ul>
 */
@AnalyzeClasses(packages = "org.albedo.vllmpt.module.approval",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ApprovalArchitectureTest {

    @ArchTest
    static final ArchRule coreMustNotDependOnBiz =
            noClasses().that().resideInAPackage("..approval.core..")
                    .should().dependOnClassesThat().resideInAPackage("..approval.biz..");

    @ArchTest
    static final ArchRule coreMustNotDependOnChat =
            noClasses().that().resideInAPackage("..approval.core..")
                    .should().dependOnClassesThat().resideInAPackage("..module.chat..");

    @ArchTest
    static final ArchRule bizMustNotDependOnChat =
            noClasses().that().resideInAPackage("..approval.biz..")
                    .should().dependOnClassesThat().resideInAPackage("..module.chat..");
}
