package com.example.btdemo;

//import java.io.File;
//import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import com.example.btdemo.DeviceListActivity;

import android.app.Activity;
//import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
//import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
//import android.os.Environment;
import android.os.Handler;
import android.os.Message;
//import android.view.LayoutInflater;
//import android.view.Menu;            //如使用菜单加入此三包
//import android.view.MenuInflater;
//import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class BTClient extends Activity {

	private final static int REQUEST_CONNECT_DEVICE = 1; // 宏定义查询设备句柄
	private final static String MY_UUID = "00001101-0000-1000-8000-00805F9B34FB"; // SPP服务UUID号

	private InputStream is; // 输入流，用来接收蓝牙数据
	// private TextView text0; //提示栏解句柄
	private EditText edit0; // 发送数据输入句柄
	private TextView dis; // 接收数据显示句柄
	private ScrollView sv; // 翻页句柄
	private String smsg = ""; // 显示用数据缓存
	private String fmsg = ""; // 保存用数据缓存

	public String filename = ""; // 用来保存存储的文件名
	BluetoothDevice _device = null; // 蓝牙设备
	BluetoothSocket _socket = null; // 蓝牙通信socket
	boolean _discoveryFinished = false;
	boolean bRun = true;
	boolean bThread = false;
	// 获取本地蓝牙适配器，即蓝牙设备
	private BluetoothAdapter _bluetooth = BluetoothAdapter.getDefaultAdapter();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main); // 设置画面为主画面 main.xml
		// 注册广播用于获得RSSI值
		/*
		 * IntentFilter filter = new IntentFilter(
		 * BluetoothDevice.ACTION_ACL_CONNECTED);
		 * this.registerReceiver(mReceiver, filter);
		 */
		// text0 = (TextView)findViewById(R.id.Text0); //得到提示栏句柄
		edit0 = (EditText) findViewById(R.id.Edit0); // 得到输入框句柄
		sv = (ScrollView) findViewById(R.id.ScrollView01); // 得到翻页句柄
		dis = (TextView) findViewById(R.id.in); // 得到数据显示句柄

		// 如果打开本地蓝牙设备不成功，提示信息，结束程序
		if (_bluetooth == null) {
			Toast.makeText(this, "无法打开手机蓝牙，请确认手机是否有蓝牙功能！", Toast.LENGTH_LONG)
					.show();
			finish();
			return;
		}

		// 设置设备可以被搜索
		new Thread() {
			public void run() {
				if (_bluetooth.isEnabled() == false) {
					_bluetooth.enable();
				}
			}
		}.start();
	}

	// 发送按键响应
	public void onSendButtonClicked(View v) {
		int i = 0;
		int n = 0;
		try {
			OutputStream os = _socket.getOutputStream(); // 蓝牙连接输出流
			byte[] bos = edit0.getText().toString().getBytes();//将输出数据放入bos数组
			for (i = 0; i < bos.length; i++) {	
				if (bos[i] == 0x0a)
					n++;
			}
			byte[] bos_new = new byte[bos.length + n];
			n = 0;
			for (i = 0; i < bos.length; i++) { // 手机中换行为0a,将其改为0d 0a后再发送
				if (bos[i] == 0x0a) {
					bos_new[n] = 0x0d;
					n++;
					bos_new[n] = 0x0a;
				} else {
					bos_new[n] = bos[i];
				}
				n++;
			}

			os.write(bos_new);
		} catch (IOException e) {
		}
	}

	// 接收活动结果，响应startActivityForResult()
	//连接设备
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE: // 连接结果，由DeviceListActivity设置返回
			// 响应返回结果
			if (resultCode == Activity.RESULT_OK) { // 连接成功，由DeviceListActivity设置返回
				// MAC地址，由DeviceListActivity设置返回
				String address = data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// 得到蓝牙设备句柄
				_device = _bluetooth.getRemoteDevice(address);

				// 用服务号得到socket
				try {
					_socket = _device.createRfcommSocketToServiceRecord(UUID
							.fromString(MY_UUID));
				} catch (IOException e) {
					Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT).show();
				}
				// 连接socket
				Button btn = (Button) findViewById(R.id.Button03);
				try {
					_socket.connect();
					Toast.makeText(this, "连接" + _device.getName() + "成功！",
							Toast.LENGTH_SHORT).show();
					btn.setText("断开");
				} catch (IOException e) {
					try {
						Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT)
								.show();
						_socket.close();
						_socket = null;
					} catch (IOException ee) {
						Toast.makeText(this, "连接失败！", Toast.LENGTH_SHORT)
								.show();
					}

					return;
				}

				// 打开接收线程
				try {
					is = _socket.getInputStream(); // 得到蓝牙数据输入流
				} catch (IOException e) {
					Toast.makeText(this, "接收数据失败！", Toast.LENGTH_SHORT).show();
					return;
				}
				if (bThread == false) {
					ReadThread.start();
					bThread = true;
				} else {
					bRun = true;
				}
			}
			break;
		default:
			break;
		}
	}

	// 接收数据线程
	Thread ReadThread = new Thread() {

		public void run() {
			int num = 0;
			byte[] buffer = new byte[1024];
			byte[] buffer_new = new byte[1024];
			int i = 0;
			int n = 0;
			bRun = true;
			// 接收线程
			while (true) {
				try {
					while (is.available() == 0) {
						while (bRun == false) {
						}
					}
					while (true) {
						num = is.read(buffer); // 读入数据
//debug*******************************************************
						System.out.println(num);
						n = 0;

						String s0 = new String(buffer, 0, num);
						fmsg += s0; // 保存收到数据
						for (i = 0; i < num; i++) {
							if ((buffer[i] == 0x0d) && (buffer[i + 1] == 0x0a)) {
								buffer_new[n] = 0x0a;
								i++;
							} else {
								buffer_new[n] = buffer[i];
							}
							n++;
						}
						String s = new String(buffer_new, 0, n);
//debug***************************************************
						System.out.println(s);
						if(s.equals("O"))
						{
							s="OK";
						}
						else if(s.equals("N"))
						{
							s="NO";
						}
						else
						{
							s="";
						}

						System.out.println(s);
						smsg += s; // 写入接收缓存
						if (is.available() == 0)
							break; // 短时间没有数据才跳出进行显示
					}
					// 发送显示消息，进行显示刷新
					handler.sendMessage(handler.obtainMessage());
				} catch (IOException e) {
				}
			}
		}
	};

	// 消息处理队列
	Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			dis.setText(smsg); // 显示数据
			sv.scrollTo(0, dis.getMeasuredHeight()); // 跳至数据最后一页
		}
	};

	// 关闭程序掉用处理部分
	public void onDestroy() {
		super.onDestroy();
		if (_socket != null) // 关闭连接socket
			try {
				_socket.close();
			} catch (IOException e) {
			}
		// _bluetooth.disable(); //关闭蓝牙服务
	}

	// 菜单处理部分
	/*
	 * @Override public boolean onCreateOptionsMenu(Menu menu) {//建立菜单
	 * MenuInflater inflater = getMenuInflater();
	 * inflater.inflate(R.menu.option_menu, menu); return true; }
	 */

	/*
	 * @Override public boolean onOptionsItemSelected(MenuItem item) { //菜单响应函数
	 * switch (item.getItemId()) { case R.id.scan:
	 * if(_bluetooth.isEnabled()==false){ Toast.makeText(this, "Open BT......",
	 * Toast.LENGTH_LONG).show(); return true; } // Launch the
	 * DeviceListActivity to see devices and do scan Intent serverIntent = new
	 * Intent(this, DeviceListActivity.class);
	 * startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE); return
	 * true; case R.id.quit: finish(); return true; case R.id.clear: smsg="";
	 * ls.setText(smsg); return true; case R.id.save: Save(); return true; }
	 * return false; }
	 */

	// 连接 按键响应函数
	public void onConnectButtonClicked(View v) {
		if (_bluetooth.isEnabled() == false) { // 如果蓝牙服务不可用则提示
			Toast.makeText(this, " 打开蓝牙中...", Toast.LENGTH_LONG).show();
			return;
		}

		// 如未连接设备则打开DeviceListActivity进行设备搜索
		Button btn = (Button) findViewById(R.id.Button03);
		if (_socket == null) {
			Intent serverIntent = new Intent(this, DeviceListActivity.class); // 跳转程序设置
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE); // 设置返回宏定义
		} else {
			// 关闭连接socket
			try {

				is.close();
				_socket.close();
				_socket = null;
				bRun = false;
				btn.setText("连接");
			} catch (IOException e) {
			}
		}
		return;
	}

	// 保存按键响应函数
	public void onSaveButtonClicked(View v) {
		try {
			OutputStream os = _socket.getOutputStream(); // 蓝牙连接输出流
			byte[] A = "S".toString().getBytes();
			os.write(A);
		} catch (IOException e) {
			Toast.makeText(getApplicationContext(), "没有连接设备", Toast.LENGTH_LONG).show();
		}
	}
	
	public void onAButtonClicked(View v) {
		try {
			OutputStream os = _socket.getOutputStream(); // 蓝牙连接输出流
			byte[] A = "A".toString().getBytes();
			os.write(A);
		} catch (IOException e) {
			Toast.makeText(getApplicationContext(), "没有连接设备", Toast.LENGTH_LONG).show();
		}
	}
	
	public void onBButtonClicked(View v) {
		try {
			OutputStream os = _socket.getOutputStream(); // 蓝牙连接输出流
			byte[] A = "B".toString().getBytes();
			os.write(A);
		} catch (IOException e) {
			Toast.makeText(getApplicationContext(), "没有连接设备", Toast.LENGTH_LONG).show();
		}
	}
	public void onCButtonClicked(View v) {
		try {
			OutputStream os = _socket.getOutputStream(); // 蓝牙连接输出流
			byte[] A = "C".toString().getBytes();
			os.write(A);
		} catch (IOException e) {
			Toast.makeText(getApplicationContext(), "没有连接设备", Toast.LENGTH_LONG).show();
		}
	}
	public void onDButtonClicked(View v) {
		try {
			OutputStream os = _socket.getOutputStream(); // 蓝牙连接输出流
			byte[] A = "D".toString().getBytes();
			os.write(A);
		} catch (IOException e) {
			Toast.makeText(getApplicationContext(), "没有连接设备", Toast.LENGTH_LONG).show();
		}
	}
	// 清除按键响应函数
	public void onClearButtonClicked(View v) {
		smsg = "";
		fmsg = "";
		dis.setText(smsg);
		return;
	}

	// 退出按键响应函数
	public void onQuitButtonClicked(View v) {
		finish();
	}
	// 获取RSSI的函数
	/*
	 * final BroadcastReceiver mReceiver = new BroadcastReceiver() {
	 * 
	 * @Override public void onReceive(Context context, Intent intent) { // TODO
	 * Auto-generated method stub String action = intent.getAction();
	 * 
	 * // 当设备开始扫描时。 if (BluetoothDevice.ACTION_FOUND.equals(action)) { //
	 * 从Intent得到blueDevice对象 BluetoothDevice device = intent
	 * .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
	 * 
	 * if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
	 * 
	 * // 信号强度。 short rssi = intent.getExtras().getShort(
	 * BluetoothDevice.EXTRA_RSSI); System.out.println(rssi);
	 * Toast.makeText(getApplicationContext(), rssi, Toast.LENGTH_SHORT).show();
	 * 
	 * }
	 * 
	 * }
	 * 
	 * } };
	 */

}