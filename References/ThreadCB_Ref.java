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
/*
*author: Martin Nenov
*mail: nenov@email.sc.edu
*Project2 - CPU scheduling
*About:
   This class is responsible for actions related to threads, including
   creating, killing, dispatching, resuming, and suspending threads.

   @OSPProject Threads
*/
public class ThreadCB extends IflThreadCB 
{
    /**
       The thread constructor. Must call 

       	   super();

       as its first statement.

       @OSPProject Threads
    */

    public ThreadCB()
    {
        // your code goes here
	super(); //inherits the base class

    }

    /**
       This method will be called once at the beginning of the
       simulation. The student can set up static variables here.
       
       @OSPProject Threads
    */
	
    private static GenericList readyQueue;  	

    public static void init()
    {
   
	readyQueue = new GenericList(); // defaut constructor is called 
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
    static public ThreadCB do_create(TaskCB task)
    {
       
	//check if the task is null, if yes call dispatcher and return null	
	if (task==null){
	dispatch();
	return (null);
	}
    
	//checks if the task has max # of threads, if yes call dispatcher and return null
	if(IflThreadCB.MaxThreadsPerTask <= task.getThreadCount()){
	
	dispatch();
	return(null);
	}
	
	//creating the thread
	ThreadCB newThread = new ThreadCB();
	//set priority
	newThread.setPriority(task.getPriority());
	//set status 
	newThread.setStatus(ThreadReady);
	//associate task with the thread
	newThread.setTask(task);
	
	//associate the thread with the task: addThread(thread), so if fails dispatch occure, if not null is return 
	if (task.addThread(newThread) == FAILURE){
	dispatch();
	return (null);
	}
	//append the new thread to the readyQueue
	readyQueue.append(newThread);
	
	//calling dispatcher
	dispatch();
	
        MyOut.print(newThread, "A thread has been created and added " + newThread);	

	return (newThread);
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
    public void do_kill()
    {
        // your code goes here
	
	//if the status is ThreadReady then remove it from the readyQueue
	if(getStatus()==ThreadReady)
	{ 
		readyQueue.remove(this);
	}
	//if it is still in running stage, then 3 sub statments as follow
	  else if (getStatus()==(ThreadRunning))
	  {	
	      //first finds the page table of the currently scheduled task
	      if(this==MMU.getPTBR().getTask().getCurrentThread())	
	      {
		MMU.setPTBR(null);// setting the page table base register to null

		getTask().setCurrentThread(null); //setting the current thread of the previously running task to null. 
	       }
	       else if (getStatus()>=ThreadWaiting)			
	       {
		//keep on going
	      
		}else 
		{       
			dispatch();//the thread dies;
		}
	 }
      	//getting the task from the thread	
	TaskCB task = getTask();
	//removing the task from the thread 
	task.removeThread(this);
	//setting status to threadKill
	setStatus(ThreadKill);

	//Looping through the device table to purge any IORB associate with this thread
	for(int i=0; i< Device.getTableSize(); i++)
	{
		Device.get(i).cancelPendingIO(this);
	}	


	//releasing resources
	ResourceCB.giveupResources(this);
	//calling dispatch
	dispatch();

	//cheking if the task has any threads left, if not kill the task
	if(this.getTask().getThreadCount()==0)
	{
		this.getTask().kill();	
	}

	
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
    public void do_suspend(Event event)
    {
        
	
	//checking the current status of the thread is
	//initilizing	
	int currentStat = this.getStatus();
	ThreadCB thread=null;
	TaskCB task=null;

	//tries to find task/thred, catches null
	try
	{
		task = MMU.getPTBR().getTask();
		thread = task.getCurrentThread();
	
	}catch(NullPointerException e){}
	
	//if the current thred is equal the thread, we need to suspend
	if(this == thread)
	{
	this.getTask().setCurrentThread(null);
	}
   
        //if the status isThreadRunning, change it to ThreadWaiting
	if(this.getStatus() == ThreadRunning)
	{
		setStatus(ThreadWaiting);
	}else if (this.getStatus() >= ThreadWaiting) //if it is already wating...
		{
			setStatus(this.getStatus() + 1); //adding one the waite a little bit longer
		}

	//popping from the queue
	readyQueue.remove(this);
	//adding this thread to the event queue
	event.addThread(this);

	//exit
	dispatch();
    }

    /** Resumes the thread.
        
	Only a thread with the status ThreadWaiting or higher
	can be resumed.  The status must be set to ThreadReady or
	decremented, respectively.
	A ready thread should be placed on the ready queue.
	
	@OSPProject Threads
    */
    public void do_resume()
    {
      

	// code came direct from the book, p.41
        if(getStatus() < ThreadWaiting)
        {
            MyOut.print(this,
            "Attempt to resume "
            + this + ", which wasn't waiting");
            return;
        }

        MyOut.print(this, "Resuming " + this);

        // Set thread's status.
        if (getStatus() == ThreadWaiting)
        {
            setStatus(ThreadReady);
        } else if (getStatus() > ThreadWaiting)
        setStatus(getStatus()-1);

        // Put the thread on the ready queue, if appropriate
        if (getStatus() == ThreadReady)
        readyQueue.append(this);

        dispatch();

    }

    /** 
        Selects a thread from the run queue and dispatches it. 

        If there is just one theread ready to run, reschedule the thread 
        currently on the processor.

        In addition to setting the correct thread status it must
        update the PTBR.
	
	@return SUCCESS or FAILURE

        @OSPProject Threads
    */
    public static int do_dispatch()
    {
  
	
	ThreadCB threadToDispatch=null;
	ThreadCB thread = null;
	TaskCB task=null;
	
	//same try catch statment used again
	try
	{ 
		task = MMU.getPTBR().getTask();
		thread = task.getCurrentThread();

	}catch(NullPointerException e){}

	//checking if the thread is dead or not
	if(thread != null)	
        {
	  task.setCurrentThread(null);
	  MMU.setPTBR(null);
	  thread.setStatus(ThreadReady);
	  readyQueue.append(thread);	
	}	 
	
	threadToDispatch = (ThreadCB)readyQueue.removeHead();
	
	//if the ready queue is empty, set the PTBR to null and return failure	
	if(threadToDispatch == null)
	{
	  MMU.setPTBR(null);
	  return FAILURE;		
	}

	//setting PTBR to point to the thread`s page table
	MMU.setPTBR(threadToDispatch.getTask().getPageTable()); 
        //set the thread as current thread
	threadToDispatch.getTask().setCurrentThread(threadToDispatch);
	//set thread status to running
        threadToDispatch.setStatus(ThreadRunning); 
	//setting interrupt timer with the value of the quantium of 50
	HTimer.set(50);
	


	return SUCCESS;	
}

    /**
       Called by OSP after printing an error message. The student can
       insert code here to print various tables and data structures in
       their state just after the error happened.  The body can be
       left empty, if this feature is not used.

       @OSPProject Threads
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
        can insert code here to print various tables and data
        structures in their state just after the warning happened.
        The body can be left empty, if this feature is not used.
       
        @OSPProject Threads
     */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
