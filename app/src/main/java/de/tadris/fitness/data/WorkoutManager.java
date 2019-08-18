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

package de.tadris.fitness.data;

import android.content.Context;

import java.util.List;

import de.tadris.fitness.Instance;
import de.tadris.fitness.util.CalorieCalculator;

public class WorkoutManager {

    public static void insertWorkout(Context context, Workout workout, List<WorkoutSample> samples){
        AppDatabase db= Instance.getInstance(context).db;


        workout.id= System.currentTimeMillis();

        // Calculating values
        double length= 0;
        for(int i= 1; i < samples.size(); i++){
            double sampleLength= samples.get(i - 1).toLatLong().sphericalDistance(samples.get(i).toLatLong());
            long timeDiff= (samples.get(i).relativeTime - samples.get(i - 1).relativeTime) / 1000;
            length+= sampleLength;
            samples.get(i).speed= Math.abs(sampleLength / timeDiff);
        }
        workout.length= (int)length;
        workout.avgSpeed= ((double) workout.length) / ((double) workout.duration / 1000);
        workout.avgPace= ((double)workout.duration / 1000 / 60) / ((double) workout.length / 1000);
        workout.calorie= CalorieCalculator.calculateCalories(workout, Instance.getInstance(context).userPreferences.weight);

        // Setting workoutId in the samples
        int i= 0;
        double topSpeed= 0;
        for(WorkoutSample sample : samples){
            i++;
            sample.id= workout.id + i;
            sample.workoutId= workout.id;
            if(sample.speed > topSpeed){
                topSpeed= sample.speed;
            }
        }

        workout.topSpeed= topSpeed;


        // Saving workout and samples
        db.workoutDao().insertWorkoutAndSamples(workout, samples.toArray(new WorkoutSample[0]));

    }

    public static void roundSpeedValues(List<WorkoutSample> samples){
        for(int i= 0; i < samples.size(); i++){
            WorkoutSample sample= samples.get(i);
            if(i == 0){
                sample.tmpRoundedSpeed= (sample.speed+samples.get(i+1).speed) / 2;
            }else if(i == samples.size()-1){
                sample.tmpRoundedSpeed= (sample.speed+samples.get(i-1).speed) / 2;
            }else{
                sample.tmpRoundedSpeed= (sample.speed+samples.get(i-1).speed+samples.get(i+1).speed) / 3;
            }
        }
    }

}
