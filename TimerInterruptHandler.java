package osp.Threads;

import osp.IFLModules.*;
import osp.Utilities.*;
import osp.Hardware.*;

/**    
    Author:     Logan Edwards
    Email:      edwardlw@email.sc.edu
    Date:       2013-02-14, R0
    
    Purpose:    The timer interrupt handler.  This class is called upon to
    handle timer interrupts. For FCFS scheduling, this class basically sits
    here to call for a thread dispatch since no time quantum is used.

   @OSPProject Threads
*/
public class TimerInterruptHandler extends IflTimerInterruptHandler
{
    /**
       This basically only needs to reset the times and dispatch
       another process. No time resets necessary for FCFS scheduling.
       
       @OSPProject Threads
    */
    public void do_handleInterrupt()
    {
        // dispatch a thread
        ThreadCB.dispatch();

    }

}
