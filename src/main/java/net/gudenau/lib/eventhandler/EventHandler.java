package net.gudenau.lib.eventhandler;

import java.util.LinkedList;
import java.util.Queue;
import net.gudenau.lib.annotation.Getter;

public class EventHandler{
    @Getter private volatile Thread thread;
    private final String name;
    private volatile boolean shouldRun = true;
    
    private final Queue<Event> eventQueue = new LinkedList<>();
    private volatile boolean propagateExceptions = false;
    
    public EventHandler(String name){
        this(name, true);
    }
    
    public EventHandler(String name, boolean autostart){
        this.name = name;
        
        if(autostart){
            thread = new Thread(this::handler);
            thread.setName(name);
            thread.setDaemon(true);
            thread.start();
        }
    }
    
    public void disableExceptionPropagation(){
        propagateExceptions = false;
    }
    
    public void enableExceptionPropagation(){
        propagateExceptions = false;
    }
    
    private void handler(){
        while(shouldRun){
            boolean empty;
            synchronized(eventQueue){
                empty = eventQueue.isEmpty();
            }
            if(empty){
                synchronized(thread){
                    try{
                        thread.wait();
                    }catch(InterruptedException e){
                        continue;
                    }
                }
            }
            
            final Event event;
            synchronized(eventQueue){
                event = eventQueue.poll();
            }
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized(event){
                try{
                    event.execute();
                }catch(Throwable t){
                    if(propagateExceptions){
                        throw new RuntimeException("Failed to execute event", t);
                    }else{
                        new RuntimeException("Failed to execute event", t).printStackTrace();
                    }
                }
                event.notify();
            }
        }
    }
    
    public void submit(final Event event) throws InterruptedException{
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized(eventQueue){
            eventQueue.add(event);
        }
        synchronized(thread){
            thread.notify();
        }
    }
    
    public void waitFor(final Event event) throws InterruptedException{
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized(event){
            synchronized(eventQueue){
                eventQueue.add(event);
            }
            synchronized(thread){
                thread.notify();
            }
            event.wait();
        }
    }
    
    public <T> T waitFor(EventWithResult<T> event) throws InterruptedException{
        EventWrapper<T> wrapper = new EventWrapper<>(event);
        waitFor(wrapper);
        return wrapper.getResult();
    }
    
    public void stop(){
        shouldRun = false;
        if(thread != null){
            synchronized(thread){
                thread.interrupt();
            }
        }
    }
    
    public Thread getThread(){
        return thread;
    }
    
    public void handleEvents(){
        if(thread == null){
            thread = Thread.currentThread();
            thread.setName(name);
            handler();
        }
    }
    
    private static class EventWrapper<T> implements Event{
        private final EventWithResult<T> event;
        @Getter private volatile T result;
    
        private EventWrapper(EventWithResult<T> event){
            this.event = event;
        }
    
        @Override
        public void execute(){
            result = event.execute();
        }
        
        T getResult(){
            return result;
        }
    }
}
