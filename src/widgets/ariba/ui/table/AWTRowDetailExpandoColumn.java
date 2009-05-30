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

    $Id: //ariba/platform/ui/widgets/ariba/ui/table/AWTRowDetailExpandoColumn.java#1 $
*/
package ariba.ui.table;

public class AWTRowDetailExpandoColumn extends AWTDataTable.ColumnRenderer
{
    public static class Column extends AWTDataTable.Column
    {
        public String rendererComponentName ()
        {
            return AWTRowDetailExpandoColumn.class.getName();
        }
    }

    public boolean isExpanded ()
    {
        return _table.displayGroup().currentDetailExpanded();
    }

    public String toggleImageName ()
    {
        return isExpanded() ? "awxToggleImageTrue.gif" : "awxToggleImageFalse.gif";
    }

    public void toggle ()
    {
        _table.displayGroup().toggleCurrentDetailExpanded();
    }
}