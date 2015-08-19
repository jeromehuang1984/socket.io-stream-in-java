package com.github.nodejs.stream;

import com.github.nodejs.vo.StreamOptions;

public interface IPipable {
	public IPipable pipe(Writable dest, StreamOptions options);
}
