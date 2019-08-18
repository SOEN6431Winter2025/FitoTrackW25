/*
 * Copyright (c) 2019 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack
 *
 * FitoTrack is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     FitoTrack is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.tadris.fitness.location;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import org.mapsforge.core.model.LatLong;

import java.util.ArrayList;
import java.util.List;

import de.tadris.fitness.Instance;
import de.tadris.fitness.data.Workout;
import de.tadris.fitness.data.WorkoutManager;
import de.tadris.fitness.data.WorkoutSample;
import de.tadris.fitness.util.CalorieCalculator;

public class WorkoutRecorder implements LocationListener.LocationChangeListener {

    private static int getMinDistance(String workoutType){
        switch (workoutType){
            case Workout.WORKOUT_TYPE_HIKING:
            case Workout.WORKOUT_TYPE_RUNNING:
                return 8;
            case Workout.WORKOUT_TYPE_CYCLING:
                return 15;
            default: return 10;
        }
    }

    private static final int PAUSE_TIME= 10000;

    private Context context;
    private Workout workout;
    private RecordingState state;
    private final List<WorkoutSample> samples= new ArrayList<>();
    private long time= 0;
    private long pauseTime= 0;
    private long lastResume;
    private long lastPause= 0;
    private long lastSampleTime= 0;
    private double distance= 0;

    public WorkoutRecorder(Context context, String workoutType) {
        this.context= context;
        this.state= RecordingState.IDLE;

        this.workout= new Workout();
        this.workout.workoutType= workoutType;
    }

    public void start(){
        if(state == RecordingState.IDLE){
            Log.i("Recorder", "Start");
            workout.start= System.currentTimeMillis();
            resume();
            Instance.getInstance(context).locationListener.registerLocationChangeListeners(this);
            startWatchdog();
        }else if(state == RecordingState.PAUSED){
            resume();
        }else if(state != RecordingState.RUNNING){
            throw new IllegalStateException("Cannot start or resume recording. state = " + state);
        }
    }

    public boolean isActive(){
        return state == RecordingState.RUNNING || state == RecordingState.PAUSED;
    }

    private void startWatchdog(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (isActive()){
                        synchronized (samples){
                            if(samples.size() > 2){
                                WorkoutSample lastSample= samples.get(samples.size()-1);
                                if(System.currentTimeMillis() - lastSampleTime > PAUSE_TIME){
                                    if(state == RecordingState.RUNNING){
                                        pause();
                                    }
                                }else{
                                    if(state == RecordingState.PAUSED){
                                        resume();
                                    }
                                }
                            }
                        }
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void resume(){
        Log.i("Recorder", "Resume");
        state= RecordingState.RUNNING;
        lastResume= System.currentTimeMillis();
        if(lastPause != 0){
            pauseTime+= System.currentTimeMillis() - lastPause;
        }
    }

    public void pause(){
        if(state == RecordingState.RUNNING){
            Log.i("Recorder", "Pause");
            state= RecordingState.PAUSED;
            time+= System.currentTimeMillis() - lastResume;
            lastPause= System.currentTimeMillis();
        }
    }

    public void stop(){
        Log.i("Recorder", "Stop");
        if(state == RecordingState.PAUSED){
            resume();
        }
        pause();
        workout.end= System.currentTimeMillis();
        workout.duration= time;
        workout.pauseDuration= pauseTime;
        state= RecordingState.STOPPED;
        Instance.getInstance(context).locationListener.unregisterLocationChangeListeners(this);
    }

    public void save(){
        if(state != RecordingState.STOPPED){
            throw new IllegalStateException("Cannot save recording, recorder was not stopped. state = " + state);
        }
        Log.i("Recorder", "Save");
        synchronized (samples){
            WorkoutManager.insertWorkout(context, workout, samples);
        }
    }

    public int getSampleCount(){
        synchronized (samples){
            return samples.size();
        }
    }

    @Override
    public void onLocationChange(Location location) {
        if(isActive()){
            double distance= 0;
            if(getSampleCount() > 0){
                synchronized (samples){
                    WorkoutSample lastSample= samples.get(samples.size() - 1);
                    distance= LocationListener.locationToLatLong(location).sphericalDistance(new LatLong(lastSample.lat, lastSample.lon));
                    long timediff= lastSample.absoluteTime - location.getTime();
                    if(distance < getMinDistance(workout.workoutType) && timediff < 500){
                        return;
                    }
                }
            }
            lastSampleTime= System.currentTimeMillis();
            if(state == RecordingState.RUNNING && location.getTime() > workout.start){
                if(samples.size() == 2){
                    lastResume= System.currentTimeMillis();
                    workout.start= System.currentTimeMillis();
                    lastPause= 0;
                    time= 0;
                    pauseTime= 0;
                    this.distance= 0;
                }
                this.distance+= distance;
                WorkoutSample sample= new WorkoutSample();
                sample.lat= location.getLatitude();
                sample.lon= location.getLongitude();
                sample.elevation= location.getAltitude();
                sample.relativeElevation= 0.0;
                sample.speed= location.getSpeed();
                sample.relativeTime= location.getTime() - workout.start - pauseTime;
                sample.absoluteTime= location.getTime();
                synchronized (samples){
                    samples.add(sample);
                }

            }
        }
    }

    /**
     * Returns the distance in meters
     */
    public int getDistance(){
        return (int)distance;
    }

    public int getCalories(){
        workout.avgSpeed= getAvgSpeed();
        return CalorieCalculator.calculateCalories(workout, Instance.getInstance(context).userPreferences.weight);
    }

    /**
     *
     * @return avgSpeed in m/s
     */
    public double getAvgSpeed(){
        return distance / (double)(getDuration() / 1000);
    }

    public long getPauseDuration(){
        if(state == RecordingState.PAUSED){
            return pauseTime + (System.currentTimeMillis() - lastPause);
        }else{
            return pauseTime;
        }
    }

    public long getDuration(){
        if(state == RecordingState.RUNNING){
            return time + (System.currentTimeMillis() - lastResume);
        }else{
            return time;
        }
    }


    enum RecordingState{
        IDLE, RUNNING, PAUSED, STOPPED
    }

}
