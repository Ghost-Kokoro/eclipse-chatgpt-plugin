package com.github.gradusnikov.eclipse.assistai.mcp.services;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;

import com.github.gradusnikov.eclipse.assistai.mcp.results.MethodSearchResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.PackageSummaryResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.TypeSearchResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.WorkspaceOverviewResponse;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Creatable
@Singleton
public class CodeDiscoveryService
{
    private static final int DEFAULT_LIMIT = 100;

    @Inject
    private ILog logger;

    public TypeSearchResponse searchTypes( String pattern, Integer maxResults )
    {
        int limit = maxResults != null && maxResults > 0 ? maxResults : DEFAULT_LIMIT;
        int collectLimit = limit * 2;

        try
        {
            var scope = SearchEngine.createWorkspaceScope();
            var engine = new SearchEngine();
            var matches = new ArrayList<TypeSearchResponse.TypeMatch>();

            int matchRule = determineMatchRule( pattern );
            char[] packagePattern = null;
            char[] typePattern = pattern.toCharArray();

            if ( pattern.contains( "." ) )
            {
                int lastDot = pattern.lastIndexOf( '.' );
                packagePattern = pattern.substring( 0, lastDot ).toCharArray();
                typePattern = pattern.substring( lastDot + 1 ).toCharArray();
            }

            engine.searchAllTypeNames(
                    packagePattern,
                    packagePattern != null ? SearchPattern.R_PATTERN_MATCH : 0,
                    typePattern,
                    matchRule,
                    IJavaSearchConstants.TYPE,
                    scope,
                    new TypeNameMatchRequestor()
                    {
                        @Override
                        public void acceptTypeNameMatch( TypeNameMatch match )
                        {
                            if ( matches.size() >= collectLimit )
                            {
                                return;
                            }
                            IType type = match.getType();
                            if ( type == null )
                            {
                                return;
                            }
                            IJavaProject project = type.getJavaProject();
                            String projectName = project != null ? project.getElementName() : null;

                            matches.add( new TypeSearchResponse.TypeMatch(
                                    match.getFullyQualifiedName(),
                                    match.getSimpleTypeName(),
                                    match.getPackageName(),
                                    projectName,
                                    typeKindLabel( type ) ) );
                        }
                    },
                    IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
                    new NullProgressMonitor() );

            return TypeSearchResponse.of( pattern, matches, limit );
        }
        catch ( JavaModelException e )
        {
            logger.error( e.getMessage(), e );
            throw new RuntimeException( "Error searching types: " + e.getMessage(), e );
        }
    }

    public MethodSearchResponse searchMethods( String pattern, String declaringTypePattern, Integer maxResults )
    {
        int limit = maxResults != null && maxResults > 0 ? maxResults : DEFAULT_LIMIT;
        int collectLimit = limit * 2;

        try
        {
            var scope = SearchEngine.createWorkspaceScope();
            var engine = new SearchEngine();
            var matches = new ArrayList<MethodSearchResponse.MethodMatch>();

            int matchRule = determineMatchRule( pattern );
            char[] methodPattern = pattern.toCharArray();
            char[] qualifyingType = declaringTypePattern != null && !declaringTypePattern.isBlank()
                    ? declaringTypePattern.toCharArray()
                    : null;

            engine.searchAllMethodNames(
                    qualifyingType,
                    qualifyingType != null ? determineMatchRule( declaringTypePattern ) : 0,
                    methodPattern,
                    matchRule,
                    scope,
                    new org.eclipse.jdt.core.search.MethodNameMatchRequestor()
                    {
                        @Override
                        public void acceptMethodNameMatch( org.eclipse.jdt.core.search.MethodNameMatch match )
                        {
                            if ( matches.size() >= collectLimit )
                            {
                                return;
                            }
                            IMethod method = match.getMethod();
                            if ( method == null )
                            {
                                return;
                            }
                            IType declaringType = method.getDeclaringType();
                            IJavaProject project = method.getJavaProject();

                            List<String> paramTypes = new ArrayList<>();
                            for ( String paramSig : method.getParameterTypes() )
                            {
                                paramTypes.add( Signature.toString( paramSig ) );
                            }

                            String returnType = null;
                            try
                            {
                                returnType = Signature.toString( method.getReturnType() );
                            }
                            catch ( JavaModelException e )
                            {
                                // ignore
                            }

                            matches.add( new MethodSearchResponse.MethodMatch(
                                    method.getElementName(),
                                    declaringType != null ? declaringType.getFullyQualifiedName() : null,
                                    declaringType != null ? declaringType.getPackageFragment().getElementName() : null,
                                    project != null ? project.getElementName() : null,
                                    returnType,
                                    paramTypes ) );
                        }
                    },
                    IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
                    new NullProgressMonitor() );

            return MethodSearchResponse.of( pattern, matches, limit );
        }
        catch ( JavaModelException e )
        {
            logger.error( e.getMessage(), e );
            throw new RuntimeException( "Error searching methods: " + e.getMessage(), e );
        }
    }

    public PackageSummaryResponse getPackageSummary( String packageName, String projectName )
    {
        try
        {
            IPackageFragment pkg = findPackage( packageName, projectName );
            if ( pkg == null )
            {
                return PackageSummaryResponse.of( packageName, projectName, List.of() );
            }

            String resolvedProject = pkg.getJavaProject().getElementName();
            var types = new ArrayList<PackageSummaryResponse.TypeSummary>();

            for ( var cu : pkg.getCompilationUnits() )
            {
                for ( IType type : cu.getTypes() )
                {
                    String javadocSummary = extractFirstSentence( type );
                    String typeKind = typeKindLabel( type );

                    int methodCount = type.getMethods().length;
                    int fieldCount = type.getFields().length;

                    List<String> superInterfaces = new ArrayList<>();
                    for ( String iface : type.getSuperInterfaceNames() )
                    {
                        superInterfaces.add( iface );
                    }

                    types.add( new PackageSummaryResponse.TypeSummary(
                            type.getElementName(),
                            typeKind,
                            javadocSummary,
                            methodCount,
                            fieldCount,
                            superInterfaces ) );
                }
            }

            return PackageSummaryResponse.of( packageName, resolvedProject, types );
        }
        catch ( JavaModelException e )
        {
            logger.error( e.getMessage(), e );
            throw new RuntimeException( "Error getting package summary: " + e.getMessage(), e );
        }
    }

    public WorkspaceOverviewResponse getWorkspaceOverview( String projectFilter, Integer maxPackagesPerProject )
    {
        int maxPkgs = maxPackagesPerProject != null && maxPackagesPerProject > 0 ? maxPackagesPerProject : 50;

        try
        {
            var projects = new ArrayList<WorkspaceOverviewResponse.ProjectOverview>();

            for ( IJavaProject javaProject : getAvailableJavaProjects() )
            {
                String name = javaProject.getElementName();
                if ( projectFilter != null && !projectFilter.isBlank() && !name.contains( projectFilter ) )
                {
                    continue;
                }

                var packageOverviews = new ArrayList<WorkspaceOverviewResponse.PackageOverview>();
                int totalTypes = 0;
                int totalPackageCount = 0;

                for ( IPackageFragmentRoot root : javaProject.getPackageFragmentRoots() )
                {
                    if ( root.getKind() != IPackageFragmentRoot.K_SOURCE )
                    {
                        continue;
                    }

                    for ( IJavaElement child : root.getChildren() )
                    {
                        if ( !( child instanceof IPackageFragment pkg ) || pkg.getCompilationUnits().length == 0 )
                        {
                            continue;
                        }

                        totalPackageCount++;

                        var typeNames = new ArrayList<String>();
                        for ( var cu : pkg.getCompilationUnits() )
                        {
                            for ( IType type : cu.getTypes() )
                            {
                                typeNames.add( type.getElementName() );
                            }
                        }

                        totalTypes += typeNames.size();

                        if ( packageOverviews.size() < maxPkgs )
                        {
                            packageOverviews.add( new WorkspaceOverviewResponse.PackageOverview(
                                    pkg.getElementName(),
                                    typeNames.size(),
                                    typeNames ) );
                        }
                    }
                }

                projects.add( new WorkspaceOverviewResponse.ProjectOverview(
                        name,
                        totalPackageCount,
                        totalTypes,
                        packageOverviews ) );
            }

            return WorkspaceOverviewResponse.of( projects );
        }
        catch ( JavaModelException e )
        {
            logger.error( e.getMessage(), e );
            throw new RuntimeException( "Error building workspace overview: " + e.getMessage(), e );
        }
    }

    private int determineMatchRule( String pattern )
    {
        if ( pattern.contains( "*" ) || pattern.contains( "?" ) )
        {
            return SearchPattern.R_PATTERN_MATCH;
        }
        if ( isCamelCasePattern( pattern ) )
        {
            return SearchPattern.R_CAMELCASE_MATCH;
        }
        return SearchPattern.R_PREFIX_MATCH | SearchPattern.R_CASE_SENSITIVE;
    }

    private boolean isCamelCasePattern( String pattern )
    {
        if ( pattern.isEmpty() )
        {
            return false;
        }
        if ( !Character.isLetter( pattern.charAt( 0 ) ) )
        {
            return false;
        }
        int upperCount = 0;
        for ( char c : pattern.toCharArray() )
        {
            if ( Character.isUpperCase( c ) )
            {
                upperCount++;
            }
        }
        return upperCount >= 2;
    }

    private String typeKindLabel( IType type )
    {
        try
        {
            if ( type.isInterface() )
            {
                return "interface";
            }
            if ( type.isEnum() )
            {
                return "enum";
            }
            if ( type.isRecord() )
            {
                return "record";
            }
            if ( type.isAnnotation() )
            {
                return "annotation";
            }
            if ( Flags.isAbstract( type.getFlags() ) )
            {
                return "abstract class";
            }
        }
        catch ( JavaModelException e )
        {
            // fall through
        }
        return "class";
    }

    private String extractFirstSentence( IType type )
    {
        try
        {
            var javadocRange = type.getJavadocRange();
            if ( javadocRange == null )
            {
                return null;
            }

            var cu = type.getCompilationUnit();
            if ( cu == null )
            {
                return null;
            }

            String cuSource = cu.getSource();
            if ( cuSource == null )
            {
                return null;
            }

            int offset = javadocRange.getOffset();
            int length = javadocRange.getLength();
            if ( offset + length > cuSource.length() )
            {
                return null;
            }

            String javadoc = cuSource.substring( offset, offset + length );
            if ( javadoc.startsWith( "/**" ) )
            {
                javadoc = javadoc.substring( 3 );
            }
            if ( javadoc.endsWith( "*/" ) )
            {
                javadoc = javadoc.substring( 0, javadoc.length() - 2 );
            }
            javadoc = javadoc.replaceAll( "(?m)^\\s*\\*\\s?", "" ).trim();

            if ( javadoc.startsWith( "@" ) )
            {
                return null;
            }

            int atIdx = javadoc.indexOf( "\n@" );
            if ( atIdx > 0 )
            {
                javadoc = javadoc.substring( 0, atIdx ).trim();
            }

            int dotIdx = javadoc.indexOf( '.' );
            if ( dotIdx > 0 && dotIdx < 200 )
            {
                return javadoc.substring( 0, dotIdx + 1 ).trim();
            }

            if ( javadoc.length() > 200 )
            {
                return javadoc.substring( 0, 200 ).trim() + "...";
            }

            return javadoc.isEmpty() ? null : javadoc;
        }
        catch ( JavaModelException e )
        {
            return null;
        }
    }

    private IPackageFragment findPackage( String packageName, String projectName ) throws JavaModelException
    {
        for ( IJavaProject project : getAvailableJavaProjects() )
        {
            if ( projectName != null && !projectName.isBlank() && !project.getElementName().equals( projectName ) )
            {
                continue;
            }

            for ( IPackageFragmentRoot root : project.getPackageFragmentRoots() )
            {
                if ( root.getKind() != IPackageFragmentRoot.K_SOURCE )
                {
                    continue;
                }

                IPackageFragment pkg = root.getPackageFragment( packageName );
                if ( pkg != null && pkg.exists() )
                {
                    return pkg;
                }
            }
        }
        return null;
    }

    private List<IJavaProject> getAvailableJavaProjects()
    {
        List<IJavaProject> javaProjects = new ArrayList<>();
        try
        {
            IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
            for ( IProject project : projects )
            {
                if ( project.isOpen() && project.hasNature( JavaCore.NATURE_ID ) )
                {
                    javaProjects.add( JavaCore.create( project ) );
                }
            }
        }
        catch ( CoreException e )
        {
            throw new RuntimeException( e );
        }
        return javaProjects;
    }
}
