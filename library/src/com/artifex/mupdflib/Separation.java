package com.artifex.mupdflib;

public class Separation
{
	String name;
	int rgba;
	int cmyk;

	public Separation(String name, int rgba, int cmyk)
	{
		this.name = name;
		this.rgba = rgba;
		this.cmyk = cmyk;
	}
}

