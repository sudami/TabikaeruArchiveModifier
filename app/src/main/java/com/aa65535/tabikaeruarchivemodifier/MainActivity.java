package com.aa65535.tabikaeruarchivemodifier;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.leon.lfilepickerlibrary.LFilePicker;
import com.leon.lfilepickerlibrary.utils.Constant;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity implements OnClickListener {
    private static final int REQUEST_CODE_FILE_PICKER = 0x1233;
    private static final int REQUEST_CODE_REQUEST_PERMISSIONS = 0x1784;

    private static final int OFFSET_CLOVER = 0x16;
    private static final int OFFSET_TICKETS = 0x1a;
    private static final int OFFSET_DATATIME = 0x049a;

    private static final int WHAT_WRITE_CALENDAR = 0x334;

    private EditText cloverInput;
    private EditText ticketsInput;
    private EditText dateInput;
    private Button cloverButton;
    private Button ticketsButton;

    private File dataDir;
    private File archive;

    private Calendar calendar;
    private Handler handler = new MyHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        File cacheDir = getExternalCacheDir();
        if (cacheDir == null) {
            Toast.makeText(this, "shared storage is not currently available.",
                    Toast.LENGTH_LONG).show();
            throw new RuntimeException("shared storage is not currently available.");
        }
        dataDir = cacheDir.getParentFile().getParentFile();
        archive = new File(dataDir, "jp.co.hit_point.tabikaeru/files/Tabikaeru.sav");
        initView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        verifyStoragePermissions(this);
    }

    private void pickArchive() {
        new LFilePicker()
                .withActivity(MainActivity.this)
                .withRequestCode(REQUEST_CODE_FILE_PICKER)
                .withTitle(getString(R.string.archive_pick))
                .withBackgroundColor("#3F51B5")
                .withFileFilter(new String[]{"sav"})
                .withMutilyMode(false)
                .withChooseMode(true)
                .withStartPath(archive.exists()
                        ? archive.getParentFile().getAbsolutePath() : dataDir.getAbsolutePath())
                .start();
    }

    private void initView() {
        cloverInput = findViewById(R.id.et_clover);
        ticketsInput = findViewById(R.id.et_tickets);
        dateInput = findViewById(R.id.et_date);
        cloverButton = findViewById(R.id.save_clover);
        ticketsButton = findViewById(R.id.save_tickets);
        findViewById(R.id.advance_date).setOnClickListener(this);
        cloverButton.setOnClickListener(this);
        ticketsButton.setOnClickListener(this);
        cloverInput.addTextChangedListener(new MyTextWatcher(cloverButton));
        ticketsInput.addTextChangedListener(new MyTextWatcher(ticketsButton));
    }

    private void initData() {
        if (archive.exists()) {
            if (archive.canWrite()) {
                String cloverData = getString(R.string.number, readInt(archive, OFFSET_CLOVER));
                String ticketsData = getString(R.string.number, readInt(archive, OFFSET_TICKETS));
                calendar = readCalendar(archive, OFFSET_DATATIME);
                cloverButton.setTag(cloverData);
                ticketsButton.setTag(ticketsData);
                cloverInput.setText(cloverData);
                ticketsInput.setText(ticketsData);
                dateInput.setText(getString(R.string.calendar, calendar));
            } else {
                showToast(R.string.archive_permission_denied);
            }
        } else {
            pickArchive();
        }
    }

    public void verifyStoragePermissions(Activity activity) {
        try {
            int permission = ActivityCompat.checkSelfPermission(activity,
                    "android.permission.WRITE_EXTERNAL_STORAGE");
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{
                        "android.permission.READ_EXTERNAL_STORAGE",
                        "android.permission.WRITE_EXTERNAL_STORAGE"
                }, REQUEST_CODE_REQUEST_PERMISSIONS);
            } else {
                initData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(@StringRes int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_REQUEST_PERMISSIONS) {
            initData();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_FILE_PICKER && data != null) {
            ArrayList<String> list = data.getStringArrayListExtra(Constant.RESULT_INFO);
            archive = new File(list.get(0));
            initData();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_archive_pick:
                pickArchive();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.save_clover:
                writeInt(v, cloverInput, OFFSET_CLOVER);
                break;
            case R.id.save_tickets:
                writeInt(v, ticketsInput, OFFSET_TICKETS);
                break;
            case R.id.advance_date:
                calendar.add(Calendar.HOUR_OF_DAY, -3);
                handler.removeMessages(WHAT_WRITE_CALENDAR);
                handler.sendEmptyMessageDelayed(WHAT_WRITE_CALENDAR, 500);
                break;
        }
    }

    private void writeInt(View view, EditText editText, int offset) {
        try {
            String s = editText.getText().toString();
            boolean ret = writeInt(archive, offset, Integer.parseInt(s));
            view.setTag(s);
            view.setEnabled(!ret);
            showToast(ret ? R.string.success_msg : R.string.failure_msg);
        } catch (NumberFormatException e) {
            showToast(R.string.number_err_msg);
        }
    }

    private void writeCalendar() {
        if (writeCalendar(archive, OFFSET_DATATIME, calendar)) {
            dateInput.setText(getString(R.string.calendar, calendar));
            showToast(R.string.success_msg);
        } else {
            showToast(R.string.failure_msg);
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    private static int readInt(File archive, int offset) {
        RandomAccessFile r = null;
        try {
            r = new RandomAccessFile(archive, "r");
            r.seek(offset);
            return r.readInt();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeQuietly(r);
        }
        return -1;
    }

    private static boolean writeInt(File archive, int offset, int value) {
        RandomAccessFile r = null;
        try {
            r = new RandomAccessFile(archive, "rwd");
            r.seek(offset);
            r.writeInt(value);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeQuietly(r);
        }
        return false;
    }

    private static Calendar readCalendar(File archive, int offset) {
        RandomAccessFile r = null;
        Calendar calendar = Calendar.getInstance();
        try {
            r = new RandomAccessFile(archive, "r");
            r.seek(offset);
            calendar.set(r.readInt(), r.readInt() - 1, r.readInt(), r.readInt(), r.readInt(), r.readInt());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeQuietly(r);
        }
        return calendar;
    }

    private static boolean writeCalendar(File archive, int offset, Calendar value) {
        RandomAccessFile r = null;
        try {
            r = new RandomAccessFile(archive, "rwd");
            r.seek(offset);
            r.writeInt(value.get(Calendar.YEAR));
            r.writeInt(value.get(Calendar.MONTH) + 1);
            r.writeInt(value.get(Calendar.DAY_OF_MONTH));
            r.writeInt(value.get(Calendar.HOUR_OF_DAY));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeQuietly(r);
        }
        return false;
    }

    private static class MyTextWatcher implements TextWatcher {
        private Button button;

        MyTextWatcher(Button button) {
            this.button = button;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            button.setEnabled(s.length() > 0 && !s.toString().equals(button.getTag()));
        }
    }

    private static class MyHandler extends Handler {
        private final WeakReference<MainActivity> reference;

        private MyHandler(MainActivity reference) {
            this.reference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = reference.get();
            if (null == activity) {
                return;
            }
            switch (msg.what) {
                case WHAT_WRITE_CALENDAR:
                    activity.writeCalendar();
                    break;
            }
        }
    }
}
