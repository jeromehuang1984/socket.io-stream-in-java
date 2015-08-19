package com.github.nodejs.stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;

import com.github.nkzawa.emitter.Emitter.Listener;
import com.github.nodejs.vo.StreamOptions;

public class FileIO {

	public String path;
	public int start = -1;
	public int end = -1;
	public boolean autoClose = true;
	public int pos = 0;
	public boolean destroyed = false;
	public boolean opened = false;
	public boolean closed = false;
	private long fileSize = 0;
	
	
	InputStream inputStream;
	FileOutputStream outputStream;
	
	public FileIO() {
		// TODO Auto-generated constructor stub
	}
	
	public static FileReadStream createReadStream(String filePath, StreamOptions options) {
		return new FileReadStream(filePath, options);
	}
	
	public static FileWriteStream createWriteStream(String filePath, StreamOptions options) {
		return new FileWriteStream(filePath, options);
	}
	
	public void openInputStream(String filePath, String picFormat, Listener cb) {
		try {
			File f = new File(filePath);
			fileSize = f.length();
            inputStream = new FileInputStream(f);
			cb.call();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			cb.call(new Error(e.getMessage()));
		}
	}
	
	public void openOutputStream(String filePath, Listener cb) {
		try {
			File newFile = new File(filePath);
			if (!newFile.exists()) {
				newFile.createNewFile();
			}
			outputStream = new FileOutputStream(newFile, true);
			cb.call();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			cb.call(new Error(e.getMessage()));
		}
	}
	
	public void closeInputStream() {
		try {
			inputStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void closeOutputStream() {
		try {
			outputStream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// fs.read(this.fd, pool, pool.used, toRead, this.pos, onread);
	public void readFromStream(byte[] target,
			int targetStart, int len, int srcStart, Listener cb) {
		try {
			long left = fileSize - srcStart;
			long bytesRead = (left < len) ? left : len;
//			inputStream.skip(srcStart);
			if (left > 0) {
				float percent = (float) (srcStart + bytesRead) / (float) fileSize;
				NumberFormat format = NumberFormat.getPercentInstance();
				format.setMaximumFractionDigits(2);
				inputStream.read(target, targetStart, (int) bytesRead);
				cb.call(null, (int) bytesRead);
			} else {
				cb.call(null, 0);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			cb.call(new Error(e.getMessage()));
		}
	}

	public void writeToStream(byte[] src, int srcStart,
			int len, Listener cb) {
		try {
			outputStream.write(src, srcStart, len);
			cb.call(null, len);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			cb.call(new Error(e.getMessage()));
		}
	}
	
	public static void makeDir(String path, Listener cb) {
		File dir = new File(path);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		cb.call();
	}
	
	public static boolean exists(String path) {
		File dir = new File(path);
		return dir.exists();
	}

    public static boolean copy(String fileFrom, String fileTo) {
        try {
            FileInputStream in = new FileInputStream(fileFrom);
            FileOutputStream out = new FileOutputStream(fileTo);
            byte[] bt = new byte[1024];
            int count;
            while ((count = in.read(bt)) > 0) {
                out.write(bt, 0, count);
            }
            in.close();
            out.close();
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public static long getSize(String filename) {
        File file = new File(filename);
        if (file.exists()) {
            return file.length();
        }
        return 0;
    }

	public static void remove(String path, Listener cb) {
		File f = new File(path);
		if (f.exists()) {
			if (f.delete()) {
				if (cb != null) {
					cb.call();
				}
			}
		}
	}

	public static  void rename(String oldPath, String newPath, Listener cb) {
		File f = new File(oldPath);
		if (f.exists()) {
			if (f.renameTo(new File(newPath))) {
				if (cb != null) {
					cb.call();
				}
			}
		}
	}
}
