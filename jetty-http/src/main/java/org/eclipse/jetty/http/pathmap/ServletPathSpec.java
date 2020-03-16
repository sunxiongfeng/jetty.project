//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.http.pathmap;

import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServletPathSpec extends PathSpec
{

    private static final Logger LOG = LoggerFactory.getLogger(ServletPathSpec.class);

    /**
     * If a servlet or filter path mapping isn't a suffix mapping, ensure
     * it starts with '/'
     *
     * @param pathSpec the servlet or filter mapping pattern
     * @return the pathSpec prefixed by '/' if appropriate
     */
    public static String normalize(String pathSpec)
    {
        if (StringUtil.isNotBlank(pathSpec) && !pathSpec.startsWith("/") && !pathSpec.startsWith("*"))
            return "/" + pathSpec;
        return pathSpec;
    }

    /**
     * @param pathSpec the path spec
     * @param path the path
     * @return true if match.
     */
    public static boolean match(String pathSpec, String path)
    {
        return match(pathSpec, path, false);
    }

    /**
     * @param pathSpec the path spec
     * @param path the path
     * @param noDefault true to not handle the default path "/" special, false to allow matcher rules to run
     * @return true if match.
     */
    public static boolean match(String pathSpec, String path, boolean noDefault)
    {
        if (pathSpec.length() == 0)
            return "/".equals(path);

        char c = pathSpec.charAt(0);
        if (c == '/')
        {
            if (!noDefault && pathSpec.length() == 1 || pathSpec.equals(path))
                return true;

            if (isPathWildcardMatch(pathSpec, path))
                return true;
        }
        else if (c == '*')
            return path.regionMatches(path.length() - pathSpec.length() + 1,
                pathSpec, 1, pathSpec.length() - 1);
        return false;
    }

    private static boolean isPathWildcardMatch(String pathSpec, String path)
    {
        // For a spec of "/foo/*" match "/foo" , "/foo/..." but not "/foobar"
        int cpl = pathSpec.length() - 2;
        if (pathSpec.endsWith("/*") && path.regionMatches(0, pathSpec, 0, cpl))
        {
            if (path.length() == cpl || '/' == path.charAt(cpl))
                return true;
        }
        return false;
    }

    /**
     * Return the portion of a path that matches a path spec.
     *
     * @param pathSpec the path spec
     * @param path the path
     * @return null if no match at all.
     */
    public static String pathMatch(String pathSpec, String path)
    {
        char c = pathSpec.charAt(0);

        if (c == '/')
        {
            if (pathSpec.length() == 1)
                return path;

            if (pathSpec.equals(path))
                return path;

            if (isPathWildcardMatch(pathSpec, path))
                return path.substring(0, pathSpec.length() - 2);
        }
        else if (c == '*')
        {
            if (path.regionMatches(path.length() - (pathSpec.length() - 1),
                pathSpec, 1, pathSpec.length() - 1))
                return path;
        }
        return null;
    }

    /**
     * Return the portion of a path that is after a path spec.
     *
     * @param pathSpec the path spec
     * @param path the path
     * @return The path info string
     */
    public static String pathInfo(String pathSpec, String path)
    {
        if ("".equals(pathSpec))
            return path; //servlet 3 spec sec 12.2 will be '/'

        char c = pathSpec.charAt(0);

        if (c == '/')
        {
            if (pathSpec.length() == 1)
                return null;

            boolean wildcard = isPathWildcardMatch(pathSpec, path);

            // handle the case where pathSpec uses a wildcard and path info is "/*"
            if (pathSpec.equals(path) && !wildcard)
                return null;

            if (wildcard)
            {
                if (path.length() == pathSpec.length() - 2)
                    return null;
                return path.substring(pathSpec.length() - 2);
            }
        }
        return null;
    }

    /**
     * Relative path.
     *
     * @param base The base the path is relative to.
     * @param pathSpec The spec of the path segment to ignore.
     * @param path the additional path
     * @return base plus path with pathspec removed
     */
    public static String relativePath(String base,
                                      String pathSpec,
                                      String path)
    {
        String info = pathInfo(pathSpec, path);
        if (info == null)
            info = path;

        if (info.startsWith("./"))
            info = info.substring(2);
        if (base.endsWith(URIUtil.SLASH))
            if (info.startsWith(URIUtil.SLASH))
                path = base + info.substring(1);
            else
                path = base + info;
        else if (info.startsWith(URIUtil.SLASH))
            path = base + info;
        else
            path = base + URIUtil.SLASH + info;
        return path;
    }

    public ServletPathSpec(String servletPathSpec)
    {
        if (servletPathSpec == null)
            servletPathSpec = "";
        if (servletPathSpec.startsWith("servlet|"))
            servletPathSpec = servletPathSpec.substring("servlet|".length());
        assertValidServletPathSpec(servletPathSpec);

        // The Root Path Spec
        if (servletPathSpec.isEmpty())
        {
            super.pathSpec = "";
            super.pathDepth = -1; // force this to be at the end of the sort order
            this.specLength = 1;
            this.group = PathSpecGroup.ROOT;
            return;
        }

        // The Default Path Spec
        if ("/".equals(servletPathSpec))
        {
            super.pathSpec = "/";
            super.pathDepth = -1; // force this to be at the end of the sort order
            this.specLength = 1;
            this.group = PathSpecGroup.DEFAULT;
            return;
        }

        this.specLength = servletPathSpec.length();
        super.pathDepth = 0;
        char lastChar = servletPathSpec.charAt(specLength - 1);
        // prefix based
        if (servletPathSpec.charAt(0) == '/' && servletPathSpec.endsWith("/*"))
        {
            this.group = PathSpecGroup.PREFIX_GLOB;
            this.prefix = servletPathSpec.substring(0, specLength - 2);
        }
        // suffix based
        else if (servletPathSpec.charAt(0) == '*' && servletPathSpec.length() > 1)
        {
            this.group = PathSpecGroup.SUFFIX_GLOB;
            this.suffix = servletPathSpec.substring(2, specLength);
        }
        else
        {
            this.group = PathSpecGroup.EXACT;
            this.prefix = servletPathSpec;
            if (servletPathSpec.endsWith("*"))
            {
                LOG.warn("Suspicious URL pattern: '{}'; see sections 12.1 and 12.2 of the Servlet specification",
                        servletPathSpec);
            }
        }

        for (int i = 0; i < specLength; i++)
        {
            int cp = servletPathSpec.codePointAt(i);
            if (cp < 128)
            {
                char c = (char)cp;
                switch (c)
                {
                    case '/':
                        super.pathDepth++;
                        break;
                    default:
                        break;
                }
            }
        }

        super.pathSpec = servletPathSpec;
    }

    private void assertValidServletPathSpec(String servletPathSpec)
    {
        if ((servletPathSpec == null) || servletPathSpec.equals(""))
        {
            return; // empty path spec
        }

        int len = servletPathSpec.length();
        // path spec must either start with '/' or '*.'
        if (servletPathSpec.charAt(0) == '/')
        {
            // Prefix Based
            if (len == 1)
            {
                return; // simple '/' path spec
            }
            int idx = servletPathSpec.indexOf('*');
            if (idx < 0)
            {
                return; // no hit on glob '*'
            }
            // only allowed to have '*' at the end of the path spec
            if (idx != (len - 1))
            {
                throw new IllegalArgumentException("Servlet Spec 12.2 violation: glob '*' can only exist at end of prefix based matches: bad spec \"" + servletPathSpec + "\"");
            }
        }
        else if (servletPathSpec.startsWith("*."))
        {
            // Suffix Based
            int idx = servletPathSpec.indexOf('/');
            // cannot have path separator
            if (idx >= 0)
            {
                throw new IllegalArgumentException("Servlet Spec 12.2 violation: suffix based path spec cannot have path separators: bad spec \"" + servletPathSpec + "\"");
            }

            idx = servletPathSpec.indexOf('*', 2);
            // only allowed to have 1 glob '*', at the start of the path spec
            if (idx >= 1)
            {
                throw new IllegalArgumentException("Servlet Spec 12.2 violation: suffix based path spec cannot have multiple glob '*': bad spec \"" + servletPathSpec + "\"");
            }
        }
        else
        {
            throw new IllegalArgumentException("Servlet Spec 12.2 violation: path spec must start with \"/\" or \"*.\": bad spec \"" + servletPathSpec + "\"");
        }
    }

    @Override
    public String getPathInfo(String path)
    {
        // Path Info only valid for PREFIX_GLOB types
        if (group == PathSpecGroup.PREFIX_GLOB)
        {
            if (path.length() == (specLength - 2))
            {
                return null;
            }
            return path.substring(specLength - 2);
        }

        return null;
    }

    @Override
    public String getPathMatch(String path)
    {
        switch (group)
        {
            case EXACT:
                if (pathSpec.equals(path))
                {
                    return path;
                }
                else
                {
                    return null;
                }
            case PREFIX_GLOB:
                if (isWildcardMatch(path))
                {
                    return path.substring(0, specLength - 2);
                }
                else
                {
                    return null;
                }
            case SUFFIX_GLOB:
                if (path.regionMatches(path.length() - (specLength - 1), pathSpec, 1, specLength - 1))
                {
                    return path;
                }
                else
                {
                    return null;
                }
            case DEFAULT:
                return path;
            default:
                return null;
        }
    }

    @Override
    public String getRelativePath(String base, String path)
    {
        String info = getPathInfo(path);
        if (info == null)
        {
            info = path;
        }

        if (info.startsWith("./"))
        {
            info = info.substring(2);
        }
        if (base.endsWith(URIUtil.SLASH))
        {
            if (info.startsWith(URIUtil.SLASH))
            {
                path = base + info.substring(1);
            }
            else
            {
                path = base + info;
            }
        }
        else if (info.startsWith(URIUtil.SLASH))
        {
            path = base + info;
        }
        else
        {
            path = base + URIUtil.SLASH + info;
        }
        return path;
    }

    private boolean isWildcardMatch(String path)
    {
        // For a spec of "/foo/*" match "/foo" , "/foo/..." but not "/foobar"
        int cpl = specLength - 2;
        if ((group == PathSpecGroup.PREFIX_GLOB) && (path.regionMatches(0, pathSpec, 0, cpl)))
        {
            if ((path.length() == cpl) || ('/' == path.charAt(cpl)))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(String path)
    {
        switch (group)
        {
            case EXACT:
                return pathSpec.equals(path);
            case PREFIX_GLOB:
                return isWildcardMatch(path);
            case SUFFIX_GLOB:
                return path.regionMatches((path.length() - specLength) + 1, pathSpec, 1, specLength - 1);
            case ROOT:
                // Only "/" matches
                return ("/".equals(path));
            case DEFAULT:
                // If we reached this point, then everything matches
                return true;
            default:
                return false;
        }
    }
}
