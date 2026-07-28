#!/usr/bin/env bash
#
# Regenerates plugins/com.github.gradusnikov.eclipse.plugin.assistai.main/docs/mcp-api.md
# from the @McpServer / @Tool / @ToolParam annotations.
#
# The generator reflects over the annotations rather than parsing source, so it needs
# the classes on an OSGi classpath - which is why this runs through the PDE test
# harness rather than as a plain java invocation. McpApiDocPDETest writes the file when
# -DupdateMcpApiDoc=true is set and otherwise asserts the committed copy matches, so
# the same class both produces the document and guards it in CI.
#
# Working in the IDE instead? Run McpApiDocPDETest through the eclipse-pde MCP server,
# or as a JUnit Plug-in Test, with updateMcpApiDoc=true in the launch's VM arguments.

set -euo pipefail

cd "$(dirname "$0")/.."

readonly DOC="plugins/com.github.gradusnikov.eclipse.plugin.assistai.main/docs/mcp-api.md"

echo "Regenerating ${DOC} from the tool annotations..."

mvn --batch-mode --quiet \
    -pl tests/com.github.gradusnikov.eclipse.plugin.assistai.main.tests \
    -am \
    -Dtest=McpApiDocPDETest \
    -DfailIfNoTests=false \
    -DupdateMcpApiDoc=true \
    -Dtycho.testArgLine="-DupdateMcpApiDoc=true" \
    verify

if ! git diff --quiet -- "${DOC}"; then
    echo "Updated. Review and commit:"
    git --no-pager diff --stat -- "${DOC}"
else
    echo "Already up to date."
fi
