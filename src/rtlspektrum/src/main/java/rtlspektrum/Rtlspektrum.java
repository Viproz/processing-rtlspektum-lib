/*
 * Copyright (C) 2015 by Pavel Sorejs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package rtlspektrum;

import rtlsdr.*;
import rtlpower.*;
import org.bridj.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Rtlspektrum 
{
	public enum RelativeModeType {
		NONE,
		RECORD,
		RELATIVE		
	}

	private int deviceId;

	private Pointer dev = null;

	private misc_settings ms = new misc_settings();
	private Pointer <tuning_state> tunes = Pointer.allocateArray(tuning_state.class,(long)RtlpowerLibrary.MAX_TUNES); 
	private Pointer <sine_table> s_tables = Pointer.allocateArray(sine_table.class,(long)RtlpowerLibrary.MAXIMUM_FFT);
	private int tune_count = 0;

	private double dbmBuffer[] = null;
	private double dbmRelativeBuffer[] = null;
	private int subbufferShifts[] = null;

	private final Lock dbmBufferLock = new ReentrantLock();

	private misc_settings.window_fn_callback window_callback = null;

	private Thread autoScanThread = null;
	private boolean stopScanThread = false;

	private RelativeModeType relMode = RelativeModeType.NONE;

	public static final int AUTO_GAIN = RtlpowerLibrary.AUTO_GAIN;


	public Rtlspektrum(int deviceId){
		this.deviceId = deviceId;
	}

	public void setRelativeMode(RelativeModeType relMode){
		this.relMode = relMode;
	}

	private void createScannerThread(){
		autoScanThread = new Thread() {
        	public void run() {
        		int i = 0;
        		while(!stopScanThread){
        			scanOneTune(i);
        			i = i < (tune_count - 1) ? i+1 : 0;
        		}
        	}
    	};		
	}
	private void initMisc(){

		window_callback = new misc_settings.window_fn_callback() {
			@Override
			public double apply(int int1, int int2) {
				return RtlpowerLibrary.rectangle(int1, int2);
			}
		};

		ms.target_rate(RtlpowerLibrary.DEFAULT_TARGET);
		ms.boxcar(1);
		ms.comp_fir_size(0);
		ms.crop(0.0);
		ms.gain(RtlpowerLibrary.AUTO_GAIN);
		ms.window_fn(Pointer.getPointer(window_callback));
		ms.smoothing(0);
		ms.peak_hold(0);
		ms.linear(0);
	}	

	public void openDevice(){
		Pointer<Pointer> ppdev = Pointer.pointerToPointer(dev);
		RtlsdrLibrary.rtlsdr_open(ppdev, 0);
		dev = ppdev.get();

		initMisc();

		RtlsdrLibrary.rtlsdr_reset_buffer(dev);
	}

	public void setFrequencyRange(int lower, int upper, int step){
		channel_solve c = new channel_solve();

		c.lower(lower);
		c.upper(upper);
		c.bin_spec(step);

		tune_count = RtlpowerLibrary.frequency_range(Pointer.getPointer(ms), tunes, Pointer.getPointer(c), tune_count);
		RtlpowerLibrary.generate_sine_tables(s_tables, tunes, tune_count);

		int bufferSize = 0;

		subbufferShifts = new int[tune_count];

		for(int i = 0;i<tune_count;i++){
			tuning_state ts = tunes.get(i);
			ts.gain(ms.gain());
			subbufferShifts[i] = bufferSize;
			bufferSize += ts.crop_i2() - ts.crop_i1() + 1;
		}

		dbmBuffer = new double[bufferSize];
		dbmRelativeBuffer = new double[bufferSize];

		for(int i = 0; i < bufferSize; i++){
			dbmRelativeBuffer[i] = Double.POSITIVE_INFINITY;
		}
	}

	public void clearFrequencyRange(){
		stopAutoScan();
		RtlpowerLibrary.free_frequency_range(tunes, tune_count);
		tune_count = 0;
	}

	public void startAutoScan(){
		if(autoScanThread != null && autoScanThread.isAlive()) return;

		createScannerThread();
		
		stopScanThread = false;
		autoScanThread.start();
	}

	public void stopAutoScan(){
		if(autoScanThread == null) return;
		if(autoScanThread.isAlive()){
			stopScanThread = true;
			try{
				autoScanThread.join();
			}catch(InterruptedException e){}
		}
	}

	public double[] getDbmBuffer(){
		if(dbmBuffer == null) return null;
		double[] ret = new double[dbmBuffer.length];

		dbmBufferLock.lock();
		try {
			System.arraycopy( dbmBuffer, 0, ret, 0, dbmBuffer.length );
		}finally{
			dbmBufferLock.unlock();
		}

		return ret;
	}

	public void setGain(int gain){
		boolean restart = autoScanThread.isAlive();
		stopAutoScan();

		ms.gain(gain);
		for(int i = 0;i<tune_count;i++){
			tuning_state ts = tunes.get(i);
			ts.gain(gain);
		}

		if(restart) startAutoScan();
	}

	public int[] getGains(){
		Pointer<Integer> gains = null;
		int count = RtlsdrLibrary.rtlsdr_get_tuner_gains(dev,gains);

		gains = Pointer.allocateInts(count);

		RtlsdrLibrary.rtlsdr_get_tuner_gains(dev,gains);

		int[] ret = new int[count];
		for(int i = 0;i<count;i++){
			ret[i] = gains.get(i);
		}

		return ret;
	}

	public void setOffsetTunning(boolean enabled){
		if(enabled){
			RtlsdrLibrary.rtlsdr_set_offset_tuning(dev,1);
		}else{
			RtlsdrLibrary.rtlsdr_set_offset_tuning(dev,0);
		}
	}

	public void setDirectSampling(int state){
		RtlsdrLibrary.rtlsdr_set_direct_sampling(dev,state);
	}	

	private void scanOneTune(int index){
		tuning_state ts = tunes.get(index);
		int bufferShift = subbufferShifts[index];

		RtlpowerLibrary.scan_tune(dev,Pointer.getPointer(ts));

		dbmBufferLock.lock();
		try {
			for (int j=0; j<=(ts.crop_i2() - ts.crop_i1()); j++) {
				dbmBuffer[bufferShift + j] = ts.dbm().get(j);

				if(relMode == RelativeModeType.RECORD){
					if(dbmRelativeBuffer[bufferShift + j] == Double.POSITIVE_INFINITY){
						dbmRelativeBuffer[bufferShift + j] = dbmBuffer[bufferShift + j];
					}else{
						dbmRelativeBuffer[bufferShift + j] = (dbmRelativeBuffer[bufferShift + j] + dbmBuffer[bufferShift + j]) / 2;
					}
					dbmBuffer[bufferShift + j] = dbmRelativeBuffer[bufferShift + j];
				}else if(relMode == RelativeModeType.RELATIVE){
					dbmBuffer[bufferShift + j] = dbmBuffer[bufferShift + j] - dbmRelativeBuffer[bufferShift + j];
				}

			}
		} finally {
			dbmBufferLock.unlock();
		}
	}
}