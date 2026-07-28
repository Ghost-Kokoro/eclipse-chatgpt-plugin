package com.github.gradusnikov.eclipse.assistai.mcp.results;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The test classes a project contains, split by the harness each one needs.
 * <p>
 * The split is the whole point of the tool: a test that touches the workspace, JDT or
 * the OSGi runtime has to be launched through the PDE harness, and this codebase encodes
 * that requirement in the class name - {@code *PDETest}. Running a PDE test through the
 * plain runner does not fail cleanly; it fails in whatever way the missing runtime
 * happens to produce.
 * <p>
 * So a caller needs two questions answered, and the old assembled listing answered them
 * only to a reader: which runner to use for each class, and which classes look
 * misnamed - they use PDE runtime types but do not carry the suffix, so they are being
 * launched the wrong way right now.
 *
 * @param namingWarnings the class names from {@link #plainTests()} that look like they
 *            need the PDE harness. Redundant with the flag on each entry, and kept
 *            because it is the actionable list - a caller wanting "what should I
 *            rename?" should not have to filter for it
 */
public record TestClassesResponse(
    String projectName,
    int totalClasses,
    List<TestClass> plainTests,
    List<TestClass> pdeTests,
    List<String> namingWarnings,
    String summaryText
)
{
    /**
     * One discovered test class.
     *
     * @param filePath relative to the project root, so the class can be handed straight
     *            to the reading and editing tools. Null when the type has no file in the
     *            workspace
     * @param likelyRequiresPdeHarness the source mentions workspace, UI or OSGi types.
     *            A heuristic over source text, so it is a warning rather than a verdict
     */
    public record TestClass(
        String className,
        String filePath,
        boolean likelyRequiresPdeHarness
    )
    {
    }

    /** Whether any plain test looks like it is being launched the wrong way. */
    public boolean hasNamingWarnings()
    {
        return !namingWarnings.isEmpty();
    }

    public static TestClassesResponse of( String projectName, List<TestClass> plainTests,
            List<TestClass> pdeTests )
    {
        List<TestClass> plain = new ArrayList<>( plainTests );
        List<TestClass> pde = new ArrayList<>( pdeTests );
        plain.sort( Comparator.comparing( TestClass::className ) );
        pde.sort( Comparator.comparing( TestClass::className ) );

        List<String> warnings = plain.stream()
                .filter( TestClass::likelyRequiresPdeHarness )
                .map( TestClass::className )
                .toList();

        int total = plain.size() + pde.size();
        String summary = total == 0
                ? "No test classes found in project '" + projectName + "'."
                : total + ( total == 1 ? " test class" : " test classes" ) + " in '" + projectName
                        + "': " + plain.size() + " plain, " + pde.size() + " PDE harness"
                        + ( warnings.isEmpty() ? "." : "; " + warnings.size() + " likely misnamed." );

        return new TestClassesResponse( projectName, total, List.copyOf( plain ), List.copyOf( pde ),
                warnings, summary );
    }
}
