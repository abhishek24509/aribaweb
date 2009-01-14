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

    $Id: //ariba/platform/ui/widgets/ariba/ui/widgets/Chooser.java#22 $
*/


package ariba.ui.widgets;

import ariba.ui.aribaweb.core.AWComponent;
import ariba.ui.aribaweb.core.AWRequestContext;
import ariba.ui.aribaweb.core.AWResponse;
import ariba.ui.aribaweb.core.AWResponseGenerating;
import ariba.ui.aribaweb.core.AWErrorManager;
import ariba.ui.aribaweb.util.AWEncodedString;
import ariba.ui.aribaweb.util.AWFormatting;
import ariba.util.core.StringUtil;
import ariba.util.core.ListUtil;
import ariba.util.core.Fmt;
import ariba.util.core.HTML;
import ariba.util.formatter.Formatter;
import java.util.List;
import java.util.Iterator;
import java.util.regex.PatternSyntaxException;

public class Chooser extends AWComponent
{
    private static int MaxLength = 10;
    public static int MaxRecentSelected = 4;
    public static String Matches = "Matches";
    public static String FilteredSelections = "FilteredSelections";

    private static final String allowFullMatchOnInput = "allowFullMatchOnInput";

    private static final String[] SupportedBindingNames = {
        BindingNames.selections, BindingNames.selectAction,
        BindingNames.selectionSource, BindingNames.maxLength,
        BindingNames.formatter, BindingNames.disabled,
        BindingNames.size, BindingNames.state,
        BindingNames.multiSelect, BindingNames.searchAction,
        BindingNames.noSelectionString,
        BindingNames.errorKey, allowFullMatchOnInput
    };

    public static String NoSelectionString = "(none selected)";
    public static String MoreSelectedString = "{0} more selected...";
    public static String NoMatchFoundString = "No match found";
    public AWEncodedString _chooserId;
    public AWEncodedString _menuId;
    private ChooserState _chooserState;
    private Object /*AWFormatting*/ _formatter;
    private boolean _disabled;
    private Object _errorKey;
    private boolean _allowFullMatchOnInput;
    private List _selectionList;

    public String[] supportedBindingNames ()
    {
        return SupportedBindingNames;
    }

    protected void awake ()
    {
        _chooserState = (ChooserState)valueForBinding(BindingNames.state);
        _formatter = (Formatter)valueForBinding(BindingNames.formatter);
        _disabled = booleanValueForBinding(BindingNames.disabled);
        _allowFullMatchOnInput = booleanValueForBinding(allowFullMatchOnInput);
    }

    protected void sleep ()
    {
        _chooserId = null;
        _menuId = null;
        _chooserState = null;
        _formatter = null;
        _disabled = false;
        _errorKey = null;
        _allowFullMatchOnInput = false;
    }

    public String noSelectionString ()
    {
        String noSelectionString = stringValueForBinding(BindingNames.noSelectionString);
        return noSelectionString != null ? noSelectionString : localizedJavaString(1, NoSelectionString);
    }

    public String behavior ()
    {
        return _disabled || isReadOnly() ? null : "CH";
    }

    public void setChooserId (AWEncodedString chooserId)
    {
        _chooserId = chooserId;
        _menuId =
            AWEncodedString.sharedEncodedString(StringUtil.strcat("CHM", chooserId.string()));
    }

    public ChooserState chooserState ()
    {
        return _chooserState;
    }

    public String currentItemString ()
    {
        Object currentItem = _chooserState.currentItem();
        return (_formatter == null)
            ? currentItem.toString()
            : AWFormatting.get(_formatter).format(_formatter, currentItem, preferredLocale());
    }

    public String currentItemHighlightedString ()
    {
        String itemString = currentItemString();
        String pattern = _chooserState.pattern();
        if (pattern != null) {
            pattern = StringUtil.escapeRegEx(pattern);
            pattern = StringUtil.strcat("(?i)(", pattern, ")");
            try {
                itemString = itemString.replaceAll(pattern, "HighlightBegin$1HighlightEnd");
            }
            catch (PatternSyntaxException e) {
                // swallow
            }
        }
        itemString = HTML.escape(itemString);
        if (pattern != null) {
            itemString = itemString.replaceAll("HighlightBegin", "<b>");
            itemString = itemString.replaceAll("HighlightEnd", "</b>");
        }
        return itemString;
    }

    private String displayObjectString ()
    {
        String displayValue = null;
        Object displayObject = _chooserState.displayObject();
        if (displayObject != null) {
            displayValue = (_formatter == null)
                ? displayObject.toString()
                : AWFormatting.get(_formatter).format(_formatter, displayObject, preferredLocale());
        }
        return displayValue;
    }

    public String displayValue ()
    {
        String displayValue = _chooserState.isInvalid() ? _chooserState.pattern() : null;
        if (displayValue == null) {
            displayValue = displayObjectString();
        }
        if (isReadOnly() && displayValue == null) {
            displayValue = noSelectionString();
        }
        return displayValue;
    }

    public void setDisplayValue (String displayValue)
    {
        if (!noSelectionString().equals(displayValue)) {
            String previouslyDisplayed = displayObjectString();
            boolean hasChanged = (previouslyDisplayed == null ||
                                  !previouslyDisplayed.equals(displayValue));
            _chooserState.hasChanged(hasChanged);
            _chooserState.setPattern(displayValue);
        }
        if (_chooserState.isInvalid()) {
            // we don't validate on every take, so read from cached condition in chooser state
            recordValidationError(errorKey(), noMatchFoundString(), _chooserState.pattern());
        }
    }

    public boolean isReadOnly ()
    {
        AWRequestContext requestContext = requestContext();
        return requestContext.isPrintMode() || requestContext.isExportMode();
    }

    public void renderResponse(AWRequestContext requestContext, AWComponent component)
    {
        if (_chooserState == null) {
            _chooserState = new ChooserState();
            boolean multiSelect = booleanValueForBinding(BindingNames.multiSelect);
            _chooserState.setMultiSelect(multiSelect);
            setValueForBinding(_chooserState, BindingNames.state);
        }
        // allow
        _chooserState.clearRecentSelectedObjects();
        if (errorManager().isValidationRequiredInAppend() &&
            _chooserState.isInvalid()) {
            // update error manager in case revalidation is required 
            recordValidationError(errorKey(), noMatchFoundString(), _chooserState.pattern());
        }
        template().elementArray()[1].renderResponse(requestContext, this);
        _chooserState.setFocus(false);
        _chooserState.setRender(false);
        requestContext.incrementElementId();
    }

    public boolean isInvalid ()
    {
        return errorManager().errantValueForKey(errorKey()) != null;
    }

    public boolean showBullet ()
    {
        return !_chooserState.multiSelect() &&
               _chooserState.isSelectedItem() &&
               !_chooserState.isInvalid();
    }

    public boolean showCheck ()
    {
        return _chooserState.multiSelect() &&
               _chooserState.isSelectedItem();
    }

    public String recentSelectedStyle ()
    {
        return ListUtil.lastElement(_chooserState.selectedObjects()) == _chooserState.currentItem() ? "display:none" : null;
    }

    public int maxRecentSelected ()
    {
        return MaxRecentSelected;
    }

    public boolean showMoreSelected ()
    {
        return _chooserState.selectedObjects().size() >= MaxRecentSelected;
    }

    private String moreSelectedString (int offset)
    {
        int moreSelected = _chooserState.selectedObjects().size() - _chooserState.recentSelectedDisplayed() + offset;
        if (moreSelected < 2) {
            return null;
        }
        String format = localizedJavaString(2, MoreSelectedString);
        return Fmt.Si(format, Integer.toString(moreSelected));
    }

    public String moreSelectedString ()
    {
        return moreSelectedString(0);
    }

    public String moreSelectedStringPlusOne ()
    {
        return moreSelectedString(1);
    }

    public String moreSelectedStringPlusTwo ()
    {
        return moreSelectedString(2);
    }

    private String noMatchFoundString ()
    {
        return localizedJavaString(3, NoMatchFoundString);
    }

    public void applyValues(AWRequestContext requestContext, AWComponent component)
    {
        template().elementArray()[1].applyValues(requestContext, this);
        requestContext.incrementElementId();
    }

    public boolean isSender ()
    {
        return _chooserId.equals(requestContext().requestSenderId());
    }

    private Object errorKey ()
    {
        if (_errorKey == null) {
            _errorKey = AWErrorManager.getErrorKeyForComponent(this);
        }
        if (_errorKey == null) {
            _errorKey = _chooserId;
        }

        return _errorKey;
    }

    public AWResponseGenerating invokeAction(AWRequestContext requestContext, AWComponent component)
    {
        AWResponseGenerating response = template().elementArray()[1].invokeAction(requestContext, this);
        if (response == null) {
            requestContext.pushElementIdLevel();
            response = template().elementArray()[3].invokeAction(requestContext, this);
            requestContext.popElementIdLevel();
        }
        else {
            requestContext.incrementElementId();
        }
        return response;
    }

    public String selectValue ()
    {
        return "-1";
    }

    public String toggleValue ()
    {
        return "-1";
    }

    public boolean getRemoveValue ()
    {
        return false;
    }

    public String getSelectionList ()
    {
        return null;
    }

    public void setSelectionList (String listId)
    {
        if (Matches.equals(listId)) {
            _selectionList = _chooserState.matches();
        }
        else if (FilteredSelections.equals(listId)) {
            _selectionList =_chooserState.filteredSelections();
        }
    }

    public void setSelectValue (String selectionIndexValue)
    {
        int selectionIndex = Integer.parseInt(selectionIndexValue);
        if (selectionIndex > -1) {
            selectAction(_selectionList, selectionIndex);
        }
    }

    public void setToggleValue (String selectionIndexValue)
    {
        int selectionIndex = Integer.parseInt(selectionIndexValue);
        if (selectionIndex > -1) {
            _chooserState.setAddMode(true);
            List selections = (List)valueForBinding(BindingNames.selections);
            selectAction(selections, selectionIndex);
        }
    }

    public void setRemoveValue (boolean removeValue)
    {
        if (removeValue) {
            _chooserState.setAddMode(true);
            _chooserState.updateSelectedObjects();
            updateState();
        }
    }

    private void selectAction (List selections, int selectionIndex)
    {
        Object item = selections.get(selectionIndex);
        _chooserState.updateSelectedObjects(item);
        updateState();
    }

    private void updateState ()
    {
        _chooserState.setFocus(true);
        _chooserState.setRender(true);
        _chooserState.setIsInvalid(false);
        _chooserState.setAddMode(false);
        clearValidationError(errorKey());
    }

    public boolean getFullMatchValue ()
    {
        return false;
    }

    public void setFullMatchValue (boolean fullMatchValue)
    {
        if (!fullMatchValue) {
            return;
        }
        String pattern = _chooserState.pattern();
        if (StringUtil.nullOrEmptyString(pattern)) {
            // if blank, and we had valid object, then set it back
            if (!_chooserState.addMode() && !_chooserState.isInvalid()) {
                Object selectedObject = _chooserState.selectedObject();
                if (selectedObject != null) {
                    _chooserState.setSelectionState(selectedObject, false);
                }
            }
            _chooserState.setIsInvalid(false);
            _chooserState.setAddMode(false);
            clearValidationError(errorKey());
        }
        else {
            match(true);
            Object match = null;
            List filterSelections = _chooserState.filteredSelections();
            if (filterSelections != null && filterSelections.size() > 0) {
                match = filterSelections.get(0);
            }
            else {
                List matches = _chooserState.matches();
                if (matches != null && matches.size() == 1) {
                    match = matches.get(0);
                }
            }
            if (match != null) {
                _chooserState.updateSelectedObjects(match);
                _chooserState.setIsInvalid(false);
                _chooserState.setAddMode(false);
                clearValidationError(errorKey());
            }
            else {
                if (!_chooserState.addMode() && !_chooserState.isInvalid()) {
                     _chooserState.setSelectionState(_chooserState.selectedObject(), false);
                }
                _chooserState.setIsInvalid(true);
                recordValidationError(errorKey(), noMatchFoundString(), pattern);
            }
        }
        _chooserState.setRender(true);
    }

    public AWResponse matchAction ()
    {
        match(_allowFullMatchOnInput);

        AWResponse response = application().createResponse();
        requestContext().setResponse(response);
        AWRequestContext requestContext = requestContext();
        template().elementArray()[3].renderResponse(requestContext, this);
        return response;
    }

    private void match (boolean fullMatch)
    {
        String pattern = request().formValueForKey("chsp");
        String addMode = request().formValueForKey("chadd");

        _chooserState.setPattern(pattern);
        if (addMode != null) {
            _chooserState.setAddMode(true);
        }
        ChooserSelectionSource selectionSource =
            (ChooserSelectionSource)valueForBinding(BindingNames.selectionSource);

        List selections = (List)valueForBinding(BindingNames.selections);
        int filteredSelectionsSize = 0;
        if (selections != null) {
            List filteredSelections = ListUtil.copyList(selections);
            Object selectedObject = _chooserState.selectedObject();
            if (_chooserState.multiSelect()) {
                List selectedOjects = _chooserState.selectedObjects();
                int size = selectedOjects.size();
                for (int i = 0; i < size; i++) {
                    selectedObject = selectedOjects.get(i);
                    if (_chooserState.addMode() ||
                        _chooserState.selectedObject() != selectedObject) {
                        filteredSelections.remove(selectedObject);
                    }
                }
            }

            filteredSelections = selectionSource.match(filteredSelections, pattern);
            _chooserState.setFilteredSelections(filteredSelections);
            filteredSelectionsSize = filteredSelections.size();
        }

        if (fullMatch && filteredSelectionsSize == 0) {
            int maxLength = intValueForBinding(BindingNames.maxLength);
            maxLength = maxLength > 0 ? maxLength : MaxLength;
            List matches = selectionSource.match(pattern, maxLength);
            _chooserState.setMatches(matches);
            if (selections != null) {
                for (Iterator iterator = selections.iterator(); iterator.hasNext();) {
                    Object o = iterator.next();
                    matches.remove(o);
                }
            }
        }
        else {
            _chooserState.setMatches(null);
        }
    }

    public AWResponseGenerating searchAction ()
    {
        if (hasBinding(BindingNames.searchAction)) {
            return (AWResponseGenerating)valueForBinding(BindingNames.searchAction);
        }
        ChooserPanel panel =
            (ChooserPanel)pageWithName(ChooserPanel.class.getName());
        ChooserSelectionSource selectionSource =
            (ChooserSelectionSource)valueForBinding(BindingNames.selectionSource);
        panel.setup(_chooserState, selectionSource, _formatter);
        return panel;
    }

}
