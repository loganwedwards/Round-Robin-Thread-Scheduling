package osp.Threads;
import java.util.Vector;
import java.util.Enumeration;
import osp.Utilities.*;
import osp.IFLModules.*;
import osp.Tasks.*;
import osp.EventEngine.*;
import osp.Hardware.*;
import osp.Devices.*;
import osp.Memory.*;
import osp.Resources.*;

/**
Author:     Logan Edwards
Email:      edwardlw@email.sc.edu
Date:       2013-04-23, R1

Purpose:    This class is responsible for actions related to threads, including
creating, killing, dispatching, resuming, and suspending threads.

@OSPProject Threads
*/
public class ThreadCB extends IflThreadCB {

    //--------------------//
    // Instance variables //
    //--------------------//
    
    // Linked list implementation for ready queue
    static GenericList readyQueue;
    
    /**
       The thread constructor.
       @OSPProject Threads
    */
    public ThreadCB() {
        super();
    }
    
    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
    public static void init() {
        
        // Initialize the ready queue
        readyQueue = new GenericList();
    }
    
    /** 
        Sets up a new thread and adds it to the given task. 
        The method must set the ready status 
        and attempt to add thread to task. If the latter fails 
        because there are already too many threads in this task, 
        so does this method, otherwise, the thread is appended 
        to the ready queue and dispatch() is called.
    
    The priority of the thread can be set using the getPriority/setPriority
    methods. However, OSP itself doesn't care what the actual value of
    the priority is. These methods are just provided in case priority
    scheduling is required.
    
    @return thread or null
    
        @OSPProject Threads
    */
    static public ThreadCB do_create(TaskCB task) {
        // Don't create a thread if the task object is null or full of threads
        if ((task == null) || (task.getThreadCount() == MaxThreadsPerTask)) {
            dispatch();
            return null;
        }
        // If we passed the previous test, actually create the thread
        ThreadCB thread = new ThreadCB();
        
        // Add reference to parent task
        thread.setPriority(task.getPriority());
        thread.setStatus(ThreadReady);
        thread.setTask(task);
        // return null if FAILURE returned from addThread()
        if (task.addThread(thread) == 0) {
            dispatch();
            return null;
        }
        
        // The final bit; add to the ready queue
        readyQueue.append(thread);
        dispatch();
        return thread;
    }
    
    /** 
    Kills the specified thread. 
    
    The status must be set to ThreadKill, the thread must be
    removed from the task's list of threads and its pending IORBs
    must be purged from all device queues.
        
    If some thread was on the ready queue, it must removed, if the 
    thread was running, the processor becomes idle, and dispatch() 
    must be called to resume a waiting thread.
    
    @OSPProject Threads
    */
    public void do_kill() {
        
        TaskCB theTask = null;
        
        // Handle the ThreadReady case
        if (this.getStatus() == ThreadReady) {
            readyQueue.remove(this);
            this.setStatus(ThreadKill);
        }
        // Handle the ThreadRunning case
        if (this.getStatus() == ThreadRunning) {
            // Do the right stuff when we find out it is the thread
            // that we want to kill
            if (MMU.getPTBR().getTask().getCurrentThread() == this) {
                MMU.setPTBR(null);
                getTask().setCurrentThread(null);
            }
        }
        
        // Handle the ThreadWaiting case
        if (this.getStatus() >= ThreadWaiting) {
            this.setStatus(ThreadKill);
        }
        // Get the associated task and kill thread
        theTask = this.getTask();
        theTask.removeThread(this);
        this.setStatus(ThreadKill);

        // Make sure IO resources are released
        for (int i = 0; i < Device.getTableSize(); i++) {
            Device.get(i).cancelPendingIO(this);
        } 
        ResourceCB.giveupResources(this);
        
        // Kill the task if no remaining threads
        // Sad, but an extra task without threads
        // is just a bit wasteful ;-)
        if (this.getTask().getThreadCount() == 0) {
            this.getTask().kill();
        }
            
    dispatch();
    }
    
    /** Suspends the thread that is currenly on the processor on the 
        specified event. 
    
        Note that the thread being suspended doesn't need to be
        running. It can also be waiting for completion of a pagefault
        and be suspended on the IORB that is bringing the page in.
    
    Thread's status must be changed to ThreadWaiting or higher,
        the processor set to idle, the thread must be in the right
        waiting queue, and dispatch() must be called to give CPU
        control to some other thread.
    
    @param event - event on which to suspend this thread.
    
        @OSPProject Threads
    */
    public void do_suspend(Event event) {
        boolean changedToWait = false;
        // Handle the ThreadRunning case
        //
        if (this.getStatus() == ThreadRunning) {
            if (MMU.getPTBR().getTask().getCurrentThread() == this) {
                MMU.setPTBR(null);
                this.getTask().setCurrentThread(null);
                this.setStatus(ThreadWaiting);
                event.addThread(this);
                changedToWait = true; // Used so we don't enter the next if below
            }   
        } // End of context switch
        
        // Handle the ThreadWaiting case
        if (this.getStatus() >= ThreadWaiting && !changedToWait) {
            this.setStatus(this.getStatus()+1);
            
            if (!readyQueue.contains(this)) {
                event.addThread(this);
                //MyOut.print("Added to the event queue");
            }
        }
        
        // Call for another thread
        dispatch();
    }
    
    /** Code from p.41 of OSP 
    Resumes the thread.
        
    Only a thread with the status ThreadWaiting or higher
    can be resumed.  The status must be set to ThreadReady or
    decremented, respectively.
    A ready thread should be placed on the ready queue.
    
    @OSPProject Threads
    */
    public void do_resume()
    {
        if(getStatus() < ThreadWaiting) {
            MyOut.print(this, "Attempt to resume " + this + ", which wasn't waiting");
            return;
        }
        
        // Message to indicate we are attempting to resume this thread
        MyOut.print(this, "Resuming " + this);
        
        // Set the thread's status
        if(this.getStatus() == ThreadWaiting) {
            setStatus(ThreadReady);
        } else if (this.getStatus() > ThreadWaiting) {
            setStatus(getStatus()-1);
        }
        
        // Put the thread on the ready queue, if appropriate
        if (getStatus() == ThreadReady) {
            readyQueue.append(this);
        }
        
        dispatch(); // dispatch a thread
    
    }
    
    /** 
        Selects a thread from the run queue and dispatches it. 
    
        If there is just one thread ready to run, reschedule the thread 
        currently on the processor.
    
        In addition to setting the correct thread status it must
        update the PTBR.
    
    @return SUCCESS or FAILURE
    
        @OSPProject Threads
    */
    public static int do_dispatch() {
        
         // Null threads to be possibly used later
         ThreadCB thread = null;
         ThreadCB newThread = null;
         
         // Handle the ThreadRunning case
        try {
            thread = MMU.getPTBR().getTask().getCurrentThread();
        }
        catch (NullPointerException e){
        // Should something smart be done here?
        // Print statements not working at compile
        }
        if (thread != null) {
        
            // thread = MMU.getPTBR().getTask().getCurrentThread();
            
            // Relinquish control of the CPU
            thread.getTask().setCurrentThread(null);
            MMU.setPTBR(null);
            
            // Make ThreadReady and add to ready queue
            thread.setStatus(ThreadReady);
            readyQueue.append(thread);
        }
        
        // If no thread running
        // Nothing in the ready queue
        if (readyQueue.isEmpty()) {
            MMU.setPTBR(null);
            //MyOut.print("Nothing in the ready queue. Returning...");
            return FAILURE; // Indicating we are not dispatching a thread
        }
        
        else {
            // If the ready queue has a thread, make it go!
            // Remove the head and cast as a ThreadCB object
            newThread = (ThreadCB)readyQueue.removeHead();
            // Get threads' task, set as the current
            MMU.setPTBR(newThread.getTask().getPageTable());
            newThread.getTask().setCurrentThread(newThread);
            newThread.setStatus(ThreadRunning);
        }
        return SUCCESS; // Hip hip hooray, we did it.
    }
    
    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.
    
       @OSPProject Threads
    */
    public static void atError() {
        // MyOut.print("Ready Queue: " + readyQueue.length());
        // MyOut.print("Crash here");
    }
    
    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning() {
       // MyOut.print("Ready Queue: " + readyQueue.length());
    
    }

}