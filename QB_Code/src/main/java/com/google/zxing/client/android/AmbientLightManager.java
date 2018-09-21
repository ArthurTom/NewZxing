/*
 * Copyright (C) 2012 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.client.android.camera.FrontLightMode;

/**
 * 实现SensorEventListener实现对传感器的监听
 *
 */
final class AmbientLightManager implements SensorEventListener {

  private static final float TOO_DARK_LUX = 45.0f;  // 光线的强度值
  private static final float BRIGHT_ENOUGH_LUX = 450.0f;

  private final Context context;
  private CameraManager cameraManager;
  private Sensor lightSensor;

  AmbientLightManager(Context context) {
    this.context = context;
  }

  void start(CameraManager cameraManager) {
    this.cameraManager = cameraManager;
    SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    if (FrontLightMode.readPref(sharedPrefs) == FrontLightMode.AUTO) {
      // 传感器管理对象
      SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
      // 获取光学传感器
      lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
      // 如果传感器不是空的
      if (lightSensor != null) {
        // 注册光学传感器，参数三是传感器更新的速度
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
      }
    }
  }

  void stop() {
    if (lightSensor != null) {
      SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
      sensorManager.unregisterListener(this);
      cameraManager = null;
      lightSensor = null;
    }
  }

  /**
   * 功能：当传感器的数值发生变化的时候走这个方法
   * @param sensorEvent
   */
  @Override
  public void onSensorChanged(SensorEvent sensorEvent) {
    float ambientLightLux = sensorEvent.values[0];  // 固定模式，第一个值是光线强度

    if (cameraManager != null) {
      if (ambientLightLux <= TOO_DARK_LUX) {
        cameraManager.setTorch(true);
        Log.d("zwk","通知显示图标"+ambientLightLux);
      } else if (ambientLightLux >= BRIGHT_ENOUGH_LUX) {
        cameraManager.setTorch(false);
      }
    }
  }

  /**
   * 功能:传感器精度发生变化走这个方法
   * @param sensor
   * @param accuracy
   */
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // do nothing
  }

}
