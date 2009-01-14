/*
    Copyright 2008 Craig Federighi

    Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
    file except in compliance with the License.
    You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    $Id: //ariba/platform/ui/metaui/ariba/ui/meta/persistence/EntityChoiceSource.java#1 $
*/
package ariba.ui.meta.persistence;

import ariba.ui.widgets.ChooserSelectionSource;

import java.util.List;

public class EntityChoiceSource implements ChooserSelectionSource
{
    String _className;

    public EntityChoiceSource (String className) { _className = className; }
    
    public List match(String pattern, int max)
    {
        // todo: create predicate!
        QuerySpecification spec = new QuerySpecification(_className);
        return ObjectContext.get().executeQuery(spec);
    }

    public List match(List selections, String pattern)
    {
        // todo: create predicate!
        QuerySpecification spec = new QuerySpecification(_className);
        return ObjectContext.get().executeQuery(spec);
    }
}
