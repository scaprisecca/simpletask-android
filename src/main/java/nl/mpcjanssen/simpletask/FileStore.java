package nl.mpcjanssen.simpletask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Environment;
import android.os.FileObserver;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import nl.mpcjanssen.simpletask.remote.FileStoreInterface;
import nl.mpcjanssen.simpletask.task.TaskBag;
import nl.mpcjanssen.simpletask.util.ListenerList;
import nl.mpcjanssen.simpletask.util.TaskIo;

/**
 * Created by a156712 on 10-6-2014.
 */
public class FileStore implements FileStoreInterface {
    private String mTodoFileName;
    private File mTodoFile;

    private final String TAG = getClass().getName();
    private FileObserver m_observer;

    public FileStore(String todoFile) {
        this.init(todoFile);
    }

    public void init (String todoFile) {
        if (todoFile.equals("")) {
            todoFile = Environment.getExternalStorageDirectory() +"/data/nl.mpcjanssen.simpletask/todo.txt";
        }
        this.mTodoFileName = todoFile;
        this.mTodoFile = new File(todoFile);
        if (!mTodoFile.exists()) {
            try {
                mTodoFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public ArrayList<String> get(TaskBag.Preferences preferences) {
        try {
            return TaskIo.loadFromFile(mTodoFile, preferences);
        } catch (IOException e) {
            ArrayList<String> failed = new ArrayList<String>();
            return failed;
        }
    }

    @Override
    public void store(String data) {
        TaskIo.writeToFile(data,mTodoFile,false);
    }

    @Override
    public void append(String data) {
        TaskIo.writeToFile(data,mTodoFile,true);

    }

    @Override
    public void prepend(String data) {

    }

    @Override
    public void startLogin(Activity caller, int i) {

    }

    @Override
    public void startWatching(final LocalBroadcastManager broadCastManager, final Intent intent) {
        if (m_observer==null) {
            m_observer = new FileObserver(mTodoFile.getParent(),
                    FileObserver.ALL_EVENTS) {
                @Override
                public void onEvent(int event, String path) {
                    if (path != null && path.equals(mTodoFileName)) {
                        if (event == FileObserver.CLOSE_WRITE ||
                                event == FileObserver.MODIFY ||
                                event == FileObserver.MOVED_TO) {
                            Log.v(TAG, path + " modified...update UI");
                            broadCastManager.sendBroadcast(intent);
                        }
                    }
                }
            };
        }
    }

    @Override
    public void stopWatching() {
        if (m_observer!=null) {
            m_observer.stopWatching();
            m_observer = null;
        }
    }

    @Override
    public boolean supportsAuthentication() {
        return false;
    }

    @Override
    public void deauthenticate() {

    }

    @Override
    public boolean isLocal() {
        return true;
    }


    @Override
    public void openNewFile(Activity act, FileSelectedListener listener) {
        FileDialog dialog = new FileDialog(act, mTodoFile, true);
        dialog.addFileListener(listener);
        dialog.createFileDialog();
    }



    private class FileDialog {
        private static final String PARENT_DIR = "..";
        private final String TAG = getClass().getName();
        private String[] fileList;
        private File currentPath;

        private ListenerList<FileSelectedListener> fileListenerList = new ListenerList<FileSelectedListener>();
        private ListenerList<DirectorySelectedListener> dirListenerList = new ListenerList<DirectorySelectedListener>();
        private final Activity activity;
        private boolean selectDirectoryOption;
        private boolean txtOnly;
        private String fileEndsWith;

        /**
         * @param activity
         * @param path
         */
        public FileDialog(Activity activity, File path, boolean txtOnly) {
            this.activity = activity;
            this.txtOnly=txtOnly;
            if (!path.exists() || !path.isDirectory()) path = Environment.getExternalStorageDirectory();
            loadFileList(path);
        }

        /**
         * @return file dialog
         */
        public Dialog createFileDialog() {
            Dialog dialog = null;
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);

            builder.setTitle(currentPath.getPath());
            if (selectDirectoryOption) {
                builder.setPositiveButton("Select directory", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        Log.d(TAG, currentPath.getPath());
                        fireDirectorySelectedEvent(currentPath);
                    }
                });
            }

            builder.setItems(fileList, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    String fileChosen = fileList[which];
                    File chosenFile = getChosenFile(fileChosen);
                    if (chosenFile.isDirectory()) {
                        loadFileList(chosenFile);
                        dialog.cancel();
                        dialog.dismiss();
                        showDialog();
                    } else fireFileSelectedEvent(chosenFile);
                }
            });

            dialog = builder.show();
            return dialog;
        }


        public void addFileListener(FileSelectedListener listener) {
            fileListenerList.add(listener);
        }

        public void removeFileListener(FileSelectedListener listener) {
            fileListenerList.remove(listener);
        }

        public void setSelectDirectoryOption(boolean selectDirectoryOption) {
            this.selectDirectoryOption = selectDirectoryOption;
        }

        public void addDirectoryListener(DirectorySelectedListener listener) {
            dirListenerList.add(listener);
        }

        public void removeDirectoryListener(DirectorySelectedListener listener) {
            dirListenerList.remove(listener);
        }

        /**
         * Show file dialog
         */
        public void showDialog() {
            createFileDialog().show();
        }

        private void fireFileSelectedEvent(final File file) {
            fileListenerList.fireEvent(new ListenerList.FireHandler<FileSelectedListener>() {
                public void fireEvent(FileSelectedListener listener) {
                    listener.fileSelected(file.toString());
                }
            });
        }

        private void fireDirectorySelectedEvent(final File directory) {
            dirListenerList.fireEvent(new ListenerList.FireHandler<DirectorySelectedListener>() {
                public void fireEvent(DirectorySelectedListener listener) {
                    listener.directorySelected(directory);
                }
            });
        }

        private void loadFileList(File path) {
            this.currentPath = path;
            List<String> r = new ArrayList<String>();
            if (path.exists()) {
                if (path.getParentFile() != null) r.add(PARENT_DIR);
                FilenameFilter filter = new FilenameFilter() {
                    public boolean accept(File dir, String filename) {
                        File sel = new File(dir, filename);
                        if (!sel.canRead()) return false;
                        if (selectDirectoryOption) return sel.isDirectory();
                        else {
                            boolean txtFile = filename.toLowerCase(Locale.getDefault()).endsWith(".txt");
                            return !txtOnly ||  sel.isDirectory() || txtFile;
                        }
                    }
                };
                String[] fileList1 = path.list(filter);
                Collections.addAll(r, fileList1);
            }
            Collections.sort(r);
            fileList = r.toArray(new String[r.size()]);
        }

        private File getChosenFile(String fileChosen) {
            if (fileChosen.equals(PARENT_DIR)) return currentPath.getParentFile();
            else return new File(currentPath, fileChosen);
        }

        public void setFileEndsWith(String fileEndsWith) {
            this.fileEndsWith = fileEndsWith != null ? fileEndsWith.toLowerCase(Locale.getDefault()) : fileEndsWith;
        }
    }
}
