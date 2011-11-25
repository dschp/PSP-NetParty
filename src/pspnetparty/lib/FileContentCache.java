package pspnetparty.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import pspnetparty.lib.constants.AppConstants;

public class FileContentCache {

	private File file;
	private String content;
	private long cacheFileSize;
	private long cacheTimestamp;

	public void setFile(String fileName) {
		file = null;
		if (!Utility.isEmpty(fileName)) {
			file = new File(fileName);
			if (!file.isFile())
				file = null;
		}

		content = "";
		cacheFileSize = cacheTimestamp = 0L;
	}

	public String getContent() {
		if (file == null || !file.isFile())
			return "";

		long fileSize = file.length();
		long lastModified = file.lastModified();
		if (fileSize != cacheFileSize || cacheTimestamp != lastModified) {
			BufferedReader br = null;
			try {
				FileInputStream fis = new FileInputStream(file);
				InputStreamReader isr = new InputStreamReader(fis, AppConstants.CHARSET);
				br = new BufferedReader(isr);

				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) {
					sb.append(line).append(AppConstants.NEW_LINE);
				}
				if (sb.length() > 0)
					sb.delete(sb.length() - AppConstants.NEW_LINE.length(), sb.length());

				content = sb.toString();
				cacheFileSize = fileSize;
				cacheTimestamp = lastModified;
			} catch (Exception e) {
				content = "";
				cacheFileSize = cacheTimestamp = 0L;
			} finally {
				try {
					if (br != null)
						br.close();
				} catch (IOException e) {
				}
			}
		}
		return content;
	}
}
