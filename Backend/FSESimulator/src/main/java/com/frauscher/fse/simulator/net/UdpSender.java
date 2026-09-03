package com.frauscher.fse.simulator.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/** Sends built packets to one fixed destination over UDP. */
public class UdpSender implements AutoCloseable {

	private final InetAddress host;
	private final int port;
	private final DatagramSocket socket;

	public UdpSender(String host, int port) throws Exception {
		this.host = InetAddress.getByName(host);
		this.port = port;
		this.socket = new DatagramSocket();
	}

	public void send(byte[] packet) throws Exception {
		socket.send(new DatagramPacket(packet, packet.length, host, port));
	}

	public String destinationDescription() {
		return host.getHostAddress() + ":" + port;
	}

	@Override
	public void close() {
		socket.close();
	}
}
