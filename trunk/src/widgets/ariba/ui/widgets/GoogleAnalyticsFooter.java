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

    $Id: //ariba/platform/ui/widgets/ariba/ui/widgets/GoogleAnalyticsFooter.java#2 $
*/

package ariba.ui.widgets;

import ariba.ui.aribaweb.core.AWComponent;
import ariba.ui.aribaweb.util.AWUtil;
import ariba.util.core.PerformanceState;
import ariba.util.core.StringUtil;

import java.util.regex.Pattern;

public class GoogleAnalyticsFooter extends AWComponent
{
    private static Pattern stripPattern = Pattern.compile("\\$?(\\[a[^\\]]*\\])?");
    
    public String trackPageName ()
    {
        String pageName =
            PerformanceState.getThisThreadHashtable().getDestinationPage();
        String trackPageName = encode(pageName);
        String pageArea =
            PerformanceState.getThisThreadHashtable().getDestinationArea();
        if (pageArea != null) {
            pageArea = encode(pageArea);
            trackPageName = StringUtil.strcat(trackPageName, "/", pageArea);
        }
        return trackPageName;
    }

    private static String encode (String name)
    {
        name = AWUtil.lastComponent(name, '/');
        name = AWUtil.lastComponent(name, '\\');
        name = stripPattern.matcher(name).replaceAll("");
        return name;
    }
}