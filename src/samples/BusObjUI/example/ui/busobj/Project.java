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

    $Id: //ariba/platform/ui/opensourceui/samples/BusObjUI/example/ui/busobj/Project.java#1 $
*/
package example.ui.busobj;

import java.util.List;
import java.math.BigDecimal;

import ariba.util.core.Date;
import ariba.util.core.ListUtil;
import ariba.ui.meta.annotations.Action;
import ariba.ui.meta.annotations.Properties;

public class Project
{
    public String _title;
    BigDecimal _description;
    protected Date _deadline;
    BigDecimal _budget;
    protected List <Deal> _deals = ListUtil.list();
    protected List <User> _team = ListUtil.list();

    enum Status { Draft, Started, Overdue, Finished }
    Status _status;

    public String getTitle ()
    {
        return _title;
    }

    public void setTitle (String title)
    {
        _title = title;
    }

    public Date getDeadline ()
    {
        return _deadline;
    }

    public void setDeadline (Date deadline)
    {
        _deadline = deadline;
    }

    /**
     * Deal list manipulation
     */
    public List getDeals ()
    {
        return _deals;
    }

    public List getTeam ()
    {
        return _team;
    }

    public List setTeam (List team)
    {
        return _team = team;
    }

    public Deal addNewDeal ()
    {
        Deal deal = new Deal();
        deal.init();
        _deals.add(deal);
        return deal;
    }

    public void removeDeal (Deal deal)
    {
        _deals.remove(deal);
    }

    public Status getStatus()
    {
        return _status;
    }

    public void setStatus(Status status)
    {
        _status = status;
    }

    @Action(message="Archived Projects: %s")
    @Properties("label:'Archive Projects'")
    public static int archiveProjects ()
    {
        return 37;
    }
    
    /**
     * Example program convenience methods
     */
    static Project _instance;
    static public Project sharedInstance ()
    {
        if (_instance == null) {
            _instance = new Project();
            _instance._title = "Project X";

            _instance._team.add(new User("Boss", "Hogg", 32));
            _instance._team.add(new User("Roscoe P.", "Coltrain", 33));
            _instance._team.add(new User("Daisy", "Duke", 87));
            _instance._team.add(new User("Bo", "Duke", 74));
            _instance._team.add(new User("Luke", "Duke", 91));
            _instance._team.add(new User("Cooter", "", 43));
            _instance._team.add(new User("Uncle Jessy", "Duke", 61));
            _instance._deadline = new Date(2002, 3, 15);
        }
        return _instance;
    }
}
