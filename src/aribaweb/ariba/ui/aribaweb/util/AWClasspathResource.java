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

    $Id: //ariba/platform/ui/aribaweb/ariba/ui/aribaweb/util/AWClasspathResource.java#2 $
*/

package ariba.ui.aribaweb.util;

import ariba.util.core.Constants;
import ariba.util.core.Assert;
import ariba.util.core.Constants;
import ariba.util.core.StringUtil;
import ariba.util.core.URLUtil;
import ariba.util.core.GrowOnlyHashtable;
import ariba.util.core.Fmt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;


public class AWClasspathResource extends AWResource
{
    URL _url;
    String _externalURL;

    public AWClasspathResource(String resourceName, String relativePath,
                               URL url, AWClasspathResourceDirectory directory)
    {
        super(resourceName, relativePath);
        _url = url;
        _externalURL = StringUtil.strcat(directory.urlPrefix(), "/", relativePath);
        // Log.aribawebResource_register.debug("Registering ClassPathResource: %s", _url.toExternalForm());
    }

    public String url()
    {
        return _externalURL;
    }

    public String fullUrl()
    {
        return _url.toExternalForm();
    }

    public long lastModified()
    {
        return 0;
    }

    public InputStream inputStream()
    {
        try {
            return _url.openStream();
        } catch (IOException e) {
            throw new AWGenericException(
              Fmt.S("Exception opening stream for ClassPath Resource: %s", url()), e);
        }
    }
}
