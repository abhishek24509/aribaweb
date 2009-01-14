/*
    Copyright 1996-2008 Ariba, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    $Id: //ariba/platform/ui/aribaweb/ariba/ui/aribaweb/util/AWNamespaceManager.java#3 $
*/

package ariba.ui.aribaweb.util;

import ariba.util.core.GrowOnlyHashtable;
import ariba.ui.aribaweb.core.AWConcreteApplication;
import ariba.ui.aribaweb.core.AWComponentDefinition;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/*
    Manages resolving namespace-based component references in .awl files.
        e.g. <a:Hyperlink> could resolve to <AWHyperlink> (in ariba.ui.aribaweb.core)
        and <a:TextButton> could resolve to <TextButton> (in ariba.ui.widgets)
        and <x:Form> could resolve to <WRCForm> (in com.customer.mycomponents)


 */
public class AWNamespaceManager
{
    static AWNamespaceManager _instance;

    static {
        _instance = new AWNamespaceManager();
    }

    protected Map<String, Resolver> _packageToResolver = new GrowOnlyHashtable();

    public static AWNamespaceManager instance()
    {
        return _instance;
    }

    public Resolver resolverForPackage (String packageName)
    {
        Resolver resolver = _packageToResolver.get(packageName);
        if (resolver == null) {
            // see if resolver registered for parent package
            if (packageName.indexOf('.') != -1) {
                String parentPackageName = AWUtil.stripLastComponent(packageName, '.');
                resolver = resolverForPackage(parentPackageName);
                if (resolver != null) {
                    registerResolverForPackage(packageName, resolver);
                }
            }
        }
        return resolver;
    }

    public void registerResolverForPackage (String packageName, Resolver resolver)
    {
        _packageToResolver.put(packageName, resolver);
    }

    public static String namespaceString (String reference)
    {
        int index = reference.indexOf(":");
        return (index == -1) ? null : reference.substring(0,index);
    }

    public static String componentName (String reference)
    {
        int index = reference.indexOf(":");
        return (index == -1) ? reference : reference.substring(index+1);
    }

    public static class Resolver
    {
        protected Resolver _fallbackResolver;
        protected Map <String, List<Import>> _includesForNamespace = new GrowOnlyHashtable();
        protected Map<String, String> _referenceToComponentName = new GrowOnlyHashtable();        

        public Resolver ()
        {

        }

        public Resolver (Resolver fallback)
        {
            this();
            _fallbackResolver = fallback;
        }

        // turn "a:AWTDataTable" into "DataTable" (or null)
        public String lookup (String referenence)
        {
            String result = null;
            if (referenence.indexOf(':') != -1) {
                // check our cache
                result = _referenceToComponentName.get(referenence);
                if (result != null) return result;

                String namespace = namespaceString(referenence);
                String componentName = componentName(referenence);
                if (namespace != null) {
                    List<Import> imports = _includesForNamespace.get(namespace);
                    if (imports != null) {
                        for (Import anImport : imports) {
                            result = anImport.lookup(componentName);
                            if (result != null) break;
                        }
                    }
                }
            }
            if (result == null && _fallbackResolver != null) {
                result = _fallbackResolver.lookup(referenence);
            }

            // cache for the future
            if (result != null) {
                _referenceToComponentName.put(referenence, result);
            }
            
            Log.aribawebResource_lookup.debug ("Namespace lookup %s --> %s", referenence, result);
            return result;
        }

        public void addIncludeToNamespace (String namespace, Import anImport)
        {
            List <Import> imports = _includesForNamespace.get(namespace);
            if (imports == null) {
                imports = new ArrayList();
                _includesForNamespace.put(namespace, imports);
            }
            imports.add(anImport);
        }
    }

    public static class AllowedGlobalsResolver extends Resolver
    {
        protected Set<String> _allowedSet = new HashSet();
        protected List<String> _allowedPrefixes = new ArrayList();

        public AllowedGlobalsResolver (Resolver fallback)
        {
            super(fallback);
        }

        public void addAllowedGlobal (String symbol)
        {
            _allowedSet.add(symbol);
        }

        public void addAllowedGlobalPrefix (String prefix)
        {
            _allowedPrefixes.add(prefix);
        }

        protected boolean allowedPrefix (String reference)
        {
            for (String prefix : _allowedPrefixes) {
                if (reference.startsWith(prefix)) return true;
            }
            return false;
        }

        public String lookup (String referenence)
        {
            return (_allowedSet.contains(referenence) || allowedPrefix(referenence)) ? referenence
                    : ((_fallbackResolver != null) ? _fallbackResolver.lookup(referenence)
                                                   : null);
        }
    }

    public static class Import
    {
        List <String> _packagePrefixes;
        List<String> _prefixes;
        public Import(List <String> packagePrefix, List<String> prefixes)
        {
            _packagePrefixes = packagePrefix;
            _prefixes = prefixes;
        }

        String lookup (String componentName)
        {
            for (String prefix : _prefixes) {
                String name = prefix.concat(componentName);
                String pkg = null;
                AWConcreteApplication application = (AWConcreteApplication)AWConcreteApplication.sharedInstance();
                AWComponentDefinition componentDefinition = application._componentDefinitionForName(name, null);
                if (componentDefinition != null) {
                    pkg = componentDefinition.componentPackageName();
                } else {
                    Class componentClass = application.resourceManager().classForName(name);
                    if (componentClass != null) {
                        Package compPkg = componentClass.getPackage();
                        pkg = (compPkg != null) ? compPkg.getName() : "";
                    }
                }

                if (pkg != null) {
                    int pkgLen = pkg.length();
                    for (String pkgPrefix : _packagePrefixes) {
                        int preLen = pkgPrefix.length();
                        if (pkg.startsWith(pkgPrefix)
                                &&  ((pkgLen == preLen) || (pkg.charAt(preLen) == '.'))) {
                            return name;
                        }
                    }
                }
            }
            return null;
        }
    }
}
