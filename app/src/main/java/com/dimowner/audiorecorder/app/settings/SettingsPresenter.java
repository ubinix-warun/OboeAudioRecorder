package com.dimowner.audiorecorder.app.settings;

import com.dimowner.audiorecorder.AppConstants;
import com.dimowner.audiorecorder.BackgroundQueue;
import com.dimowner.audiorecorder.data.FileRepository;
import com.dimowner.audiorecorder.data.Prefs;
import com.dimowner.audiorecorder.data.database.LocalRepository;
import com.dimowner.audiorecorder.data.database.Record;
import com.dimowner.audiorecorder.util.AndroidUtils;
import com.dimowner.audiorecorder.util.FileUtil;
import com.dimowner.audiorecorder.util.TimeUtils;

import java.util.List;

public class SettingsPresenter implements SettingsContract.UserActionsListener {

	private SettingsContract.View view;

	private final BackgroundQueue recordingsTasks;
	private final FileRepository fileRepository;
	private final LocalRepository localRepository;
	private final BackgroundQueue loadingTasks;
	private final Prefs prefs;

	public SettingsPresenter(final LocalRepository localRepository, FileRepository fileRepository,
									 BackgroundQueue recordingsTasks, final BackgroundQueue loadingTasks, Prefs prefs) {
		this.localRepository = localRepository;
		this.fileRepository = fileRepository;
		this.recordingsTasks = recordingsTasks;
		this.loadingTasks = loadingTasks;
		this.prefs = prefs;
	}

	@Override
	public void loadSettings() {
		view.showProgress();
		loadingTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				final List<Long> durations = localRepository.getRecordsDurations();
				long totalDuration = 0;
				for (int i = 0; i < durations.size(); i++) {
					totalDuration += durations.get(i);
				}
				final long finalTotalDuration = totalDuration;
				AndroidUtils.runOnUIThread(new Runnable() {
					@Override public void run() {
						view.showTotalRecordsDuration(TimeUtils.formatTimeIntervalHourMinSec(finalTotalDuration / 1000));
						view.showRecordsCount(durations.size());
						updateAvailableSpace();
						view.hideProgress();
					}
				});
			}
		});
		view.showStoreInPublicDir(prefs.isStoreDirPublic());
		view.showRecordInStereo(prefs.getRecordChannelCount() == AppConstants.RECORD_AUDIO_STEREO);
		view.showKeepScreenOn(prefs.isKeepScreenOn());
		view.showRecordingFormat(prefs.getFormat());

		int pos;
		switch (prefs.getSampleRate()) {
			case AppConstants.RECORD_SAMPLE_RATE_8000:
				pos = 0;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_16000:
				pos = 1;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_32000:
				pos = 2;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_48000:
				pos = 4;
				break;
			case AppConstants.RECORD_SAMPLE_RATE_44100:
			default:
				pos = 3;
		}
		view.showRecordingSampleRate(pos);
	}

	@Override
	public void storeInPublicDir(boolean b) {
		prefs.setStoreDirPublic(b);
	}

	@Override
	public void keepScreenOn(boolean keep) {
		prefs.setKeepScreenOn(keep);
	}

	@Override
	public void recordInStereo(boolean stereo) {
		prefs.setRecordInStereo(stereo);
		updateAvailableSpace();
	}

	@Override
	public void setRecordingQuality(int quality) {
		prefs.setQuality(quality);
		updateAvailableSpace();
	}

	@Override
	public void setRecordingFormat(int format) {
		prefs.setFormat(format);
		updateAvailableSpace();
	}

	@Override
	public void setSampleRate(int rate) {
		prefs.setSampleRate(rate);
		updateAvailableSpace();
	}

	@Override
	public void deleteAllRecords() {
		recordingsTasks.postRunnable(new Runnable() {
			@Override
			public void run() {
				List<Record> records  = localRepository.getAllRecords();
				for (int i = 0; i < records.size(); i++) {
					fileRepository.deleteRecordFile(records.get(i).getPath());
				}
				boolean b2 = localRepository.deleteAllRecords();
				prefs.setActiveRecord(-1);
				if (b2) {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showAllRecordsDeleted();
							}
						}});
				} else {
					AndroidUtils.runOnUIThread(new Runnable() {
						@Override
						public void run() {
							if (view != null) {
								view.showFailDeleteAllRecords();
							}
						}});
				}
			}
		});
	}

	@Override
	public void bindView(SettingsContract.View view) {
		this.view = view;
		this.localRepository.open();
	}

	@Override
	public void unbindView() {
		this.localRepository.close();
		this.view = null;
	}

	@Override
	public void clear() {
		if (view != null) {
			unbindView();
		}
	}

	private void updateAvailableSpace() {
		final long space = FileUtil.getFree(fileRepository.getRecordingDir());
		final long time = spaceToTimeSecs(space, prefs.getFormat(), prefs.getSampleRate(), prefs.getRecordChannelCount());
		view.showAvailableSpace(TimeUtils.formatTimeIntervalHourMinSec(time));
	}

	private long spaceToTimeSecs(long spaceBytes, int format, int sampleRate, int channels) {
		if (format == AppConstants.RECORDING_FORMAT_M4A) {
			return 1000 * (spaceBytes/(AppConstants.RECORD_ENCODING_BITRATE/8));
		} else if (format == AppConstants.RECORDING_FORMAT_WAV) {
			return 1000 * (spaceBytes/(sampleRate * channels * 2));
		} else {
			return 0;
		}
	}
}
