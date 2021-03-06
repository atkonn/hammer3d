package jp.co.qsdn.android.hammer3d.setting;

import android.app.Dialog;

import android.content.Context;
import android.content.res.Resources;

import android.preference.DialogPreference;
import android.preference.PreferenceManager;

import android.util.AttributeSet;
import android.util.Log;

import android.view.View;

import android.widget.Button;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SeekBar;
import android.widget.TextView;

import jp.co.qsdn.android.hammer3d.R;

public class ShumokuCountDialog 
  extends DialogPreference {
  private static final boolean _debug = false;
  private static final String TAG = ShumokuCountDialog.class.getName();
  private static SeekBar seekBar = null;
  private static final int MIN = 1;

  public ShumokuCountDialog(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public ShumokuCountDialog(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  @Override
  public View onCreateDialogView() {
    View view = super.onCreateDialogView();
    if (view != null) {
      seekBar = (SeekBar)view.findViewById(R.id.seek);
      seekBar.setProgress(Prefs.getInstance(getContext()).getShumokuCount() - MIN);
      final TextView nowCountView = (TextView)view.findViewById(R.id.dialog_now_count);

      Resources res = view.getResources();
      final String label = res.getString(R.string.dialog_shumoku_count_label);

      nowCountView.setText(label + (seekBar.getProgress() + MIN));
      seekBar.setOnSeekBarChangeListener(
        new OnSeekBarChangeListener() {
          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {
            nowCountView.setText(label + (seekBar.getProgress() + MIN));
          }
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
            nowCountView.setText(label + (seekBar.getProgress() + MIN));
          }
          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            nowCountView.setText(label + (seekBar.getProgress() + MIN));
          }
      });
      final SeekBar __seekBar = seekBar;
      Button button = (Button)view.findViewById(R.id.to_default_button);
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          __seekBar.setProgress(Prefs.DEFAULT_SHUMOKU_COUNT - MIN);
          nowCountView.setText(label + (__seekBar.getProgress() + MIN));
        }
      });
    }
    return view;
  }

  @Override
  protected void onDialogClosed (boolean positiveResult) {
    if (_debug) Log.d(TAG, "start onDialogClosed(" + positiveResult + ")");
    if (positiveResult) {
      if (seekBar != null) {
        Prefs.getInstance(getContext()).setShumokuCount((seekBar.getProgress() + MIN));
        seekBar = null;
      }
    }
    super.onDialogClosed(positiveResult);
    if (_debug) Log.d(TAG, "end onDialogClosed(" + positiveResult + ")");
  }

}


