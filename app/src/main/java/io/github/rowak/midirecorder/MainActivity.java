package io.github.rowak.midirecorder;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity
{
	private int time;
	private Timer timer;
	private SharedPreferences prefs;
	private TextView txtStatus;
	private EditText txtFileName;
	private Button btnRecord;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		btnRecord = findViewById(R.id.btnRecord);
		btnRecord.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (btnRecord.getText().equals("Record") &&
						!txtFileName.getText().toString().isEmpty())
				{
					sendRequest("start_recording " +
							txtFileName.getText().toString()
									.replace(" ", "`") + ".midi");
				}
				else if (btnRecord.getText().equals("Stop Recording"))
				{
					sendRequest("stop_recording");
				}
			}
		});
		txtFileName = findViewById(R.id.txtFileName);
		txtStatus = findViewById(R.id.txtStatus);
		
		prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		
		ActivityCompat.requestPermissions(this,
				new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
				1);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_change_server:
				showChangeServerDialog();
				return true;
			default:
				return false;
		}
	}
	
	private void startTimer()
	{
		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{
				final String timeFormatted = formatTime(time);
				txtStatus.post(new Runnable()
				{
					@Override
					public void run()
					{
						txtStatus.setText(timeFormatted);
					}
				});
				time++;
			}
		}, 0, 1000);
	}
	
	private void stopTimer()
	{
		timer.cancel();
		timer.purge();
		time = 0;
	}
	
	private void sendRequest(final String request)
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					String[] server = prefs.getString("server",
							"").split(":");
					String hostname = server[0];
					int port = -1;
					if (server.length == 2)
					{
						try
						{
							port = Integer.parseInt(server[1]);
						}
						catch (NumberFormatException nfe)
						{
							nfe.printStackTrace();
						}
					}
					if (!hostname.isEmpty() && port != -1)
					{
						Socket socket = new Socket(hostname, port);
						PrintWriter writer =
								new PrintWriter(socket.getOutputStream(), true);
						BufferedReader reader =
								new BufferedReader(
										new InputStreamReader(socket.getInputStream()));
						writer.println(request);
						String data;
						while ((data = reader.readLine()) != null)
						{
							handleResponse(request, data);
						}
					}
					else
					{
						makeToast("Server is invalid or unset");
					}
				}
				catch (IOException ioe)
				{
					ioe.printStackTrace();
					makeToast("Server connection error");
				}
			}
		}.start();
	}
	
	private void handleResponse(String request, String response)
	{
		String[] data = response.split(" ");
		switch (data[0])
		{
			case "ok":
				if (request.startsWith("start_recording"))
				{
					btnRecord.post(new Runnable()
					{
						@Override
						public void run()
						{
							btnRecord.setText("Stop Recording");
						}
					});
					startTimer();
				}
				else if (request.startsWith("stop_recording"))
				{
					stopTimer();
					btnRecord.post(new Runnable()
					{
						@Override
						public void run()
						{
							btnRecord.setText("Record");
						}
					});
					txtStatus.post(new Runnable()
					{
						@Override
						public void run()
						{
							txtStatus.setText("Not Recording");
						}
					});
					byte[] fileData = stringToBytes(data[1]);
					String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
					try
					{
						File file = new File(dir + "/Download/" +
								txtFileName.getText().toString() + ".midi");
						makeToast("Saving file to " + file.getAbsolutePath() + "...");
						try (FileOutputStream fos = new FileOutputStream(file.getAbsolutePath()))
						{
							fos.write(fileData);
						}
					}
					catch (IOException ioe)
					{
						ioe.printStackTrace();
					}
				}
				break;
			case "error":
				makeToast(response);
				break;
		}
	}
	
	private void makeToast(final String message)
	{
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				Toast.makeText(MainActivity.this,
						message, Toast.LENGTH_LONG).show();
			}
		});
	}
	
	private void showChangeServerDialog()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this,
				R.style.DialogThemeDark);
		builder.setTitle("Server address");
		
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		input.setHint("IP:port");
		builder.setView(input);
		
		builder.setPositiveButton("OK", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				final SharedPreferences.Editor editor = prefs.edit();
				editor.putString("server", input.getText().toString());
				editor.commit();
			}
		});
		builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				dialog.cancel();
			}
		});
		builder.show();
	}
	
	private byte[] stringToBytes(String str)
	{
		String[] data = str.split("_");
		byte[] bytes = new byte[data.length];
		for (int i = 0; i < data.length; i++)
		{
			bytes[i] = (byte)Integer.parseInt(data[i]);
		}
		return bytes;
	}
	
	private String formatTime(int time)
	{
		int hours = (int)((time / (60*60)) % 24);
		int minutes = (int)((time / (60)) % 60);
		int seconds = (int)time % 60;
		String hoursStr = String.format("%02d", hours);
		String minutesStr = String.format("%02d", minutes);
		String secondsStr = String.format("%02d", seconds);
		if (hours > 0)
		{
			return hoursStr + ":" + minutesStr + ":" + secondsStr;
		}
		else
		{
			return minutesStr + ":" + secondsStr;
		}
	}
}
