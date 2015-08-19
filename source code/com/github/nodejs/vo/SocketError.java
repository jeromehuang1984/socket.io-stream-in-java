package com.github.nodejs.vo;

public class SocketError extends Error {

	public boolean remote;
	public SocketError() {
		// TODO Auto-generated constructor stub
	}

	public SocketError(String detailMessage) {
		super(detailMessage);
		// TODO Auto-generated constructor stub
	}

	public SocketError(Throwable throwable) {
		super(throwable);
		// TODO Auto-generated constructor stub
	}

	public SocketError(String detailMessage, Throwable throwable) {
		super(detailMessage, throwable);
		// TODO Auto-generated constructor stub
	}

}
