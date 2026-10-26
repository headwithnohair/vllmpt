@echo off
cd /d %~dp0
call .\mvnw.cmd -q "-Dtest=ApprovalPromptBuilderTest,ApprovalToolProviderTest" test
