package edu.stanford.epad.epadws.controllers.exceptions;

import java.util.Date;

public class ForbiddenException extends ControllerException {

	public ForbiddenException() {
		super();
		// TODO Auto-generated constructor stub
	}

	public ForbiddenException(String message, Date date, String level) {
		super(message, date, level);
		// TODO Auto-generated constructor stub
	}

	public ForbiddenException(String message, String level) {
		super(message, level);
		// TODO Auto-generated constructor stub
	}

	public ForbiddenException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

}
