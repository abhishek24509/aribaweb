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

    $Id: //ariba/platform/ui/widgets/ariba/ui/widgets/AribaPageContent.java#20 $
*/

package ariba.ui.widgets;

import ariba.ui.aribaweb.core.AWComponent;
import ariba.ui.aribaweb.core.AWRequestContext;
import ariba.ui.aribaweb.core.AWSession;

public final class AribaPageContent extends BrandingComponent
{
    public static final String MessageKey = "AribaPageContentMessage";
    public static final String StickyMessageKey = "AribaPageContentStickyMessage";

    private AWComponent _context = null;
    private Object _oneTimeMessage = null;
    private AWComponent _stickyMessage = null;

    public void awake ()
    {
        super.awake();
        _context = BrandingComponent.componentWithTemplateNamed("body",this);
    }

    public void renderResponse(AWRequestContext requestContext, AWComponent component)
    {
        AWSession session = requestContext.session(false);
        if (session != null) {
            _oneTimeMessage = session.dict().get(MessageKey);
            _stickyMessage = (AWComponent)session.dict().get(StickyMessageKey);
            session.dict().remove(MessageKey);
        }

        super.renderResponse(requestContext, component);
    }

    public void sleep ()
    {
        super.sleep();
        _context = null;
        _oneTimeMessage = null;
        _stickyMessage = null;
    }

    public String bodyHeader ()
    {
        // Note this component is expected to be referenced from within a component
        // which itself is referenced from within a PageWrapper.
        return resolveTemplateOrComponentBasedInclude(BindingNames.bodyHeader);
    }

    public String contentTemplateName ()
    {
        return (_context == null ? null : "body");
    }

    public AWComponent context ()
    {
        return _context == null ? parent() : _context;
    }

    public boolean addContentMargin ()
    {
        Boolean addContentMargin;

        addContentMargin = (Boolean)findValueForBinding(BindingNames.addContentMargin);
        return addContentMargin != null ? addContentMargin.booleanValue() : true;
    }

    public Object getMessage ()
    {
        if (_oneTimeMessage != null) {
            return _oneTimeMessage;
        }
        return _stickyMessage;
    }

    public boolean isTextMessage ()
    {
        return (getMessage() instanceof String);
    }

    public static void setMessage (Object message, AWSession session)
    {
        setMessage(message, false, session);
    }

    public static void setMessage (Object message, boolean sticky, AWSession session)
    {
        if (sticky) {
            session.dict().put(StickyMessageKey, message);
        }
        else {
            session.dict().put(MessageKey, message);
        }
    }

    public static void clearMessage (AWSession session)
    {
        clearMessage(false, session);
    }

    public static void clearMessage (boolean sticky, AWSession session)
    {
        if (sticky) {
            session.dict().remove(StickyMessageKey);
        }
        else {
            session.dict().remove(MessageKey);
        }
    }

    public boolean isNested () {
        return env().peek("inAPageContent") != null;
    }
}
