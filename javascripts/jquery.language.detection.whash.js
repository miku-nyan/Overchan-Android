/*!
 * jQuery Language detection plugin v1.0
 * jQuery plugin for browser language detection with automatic redirection 
 *
 * https://github.com/danieledesantis/jquery-language-detection
 *
 * Released under the MIT License
 * Copyright 2014 Daniele De Santis
 */
(function($) {
	
	// METHODS
	var methods = {
		// INIT
		init : function(options) {			
			var options = $.extend({
				domain      :    window.location.protocol + '//' + window.location.hostname + '/',
				useFullPaths:    false,
				languages   :    [
					{
						code   :   'en',
						path   :   '',
						defaultLanguage   :   true
					}
				],
				expires    :     null				
			}, options);			
			$(document).on('click', '.language', function(e) {
				e.preventDefault();
				$(document).languageDetection('setLanguage', options.domain, options.useFullPaths, options.languages, options.expires, $(this).attr('data-language'));
			});			
			var cookie = getCookieLang();
			var language = !cookie ? navigator.language || navigator.userLanguage : cookie;
			methods.setLanguage(options.domain, options.useFullPaths, options.languages, options.expires, language);			
		},
		// SETLANGUAGE
		setLanguage : function(domain, useFullPaths, languages, expires, lang) {			
			var newPath, newLanguage;			
			for(var i = 0; i < languages.length; i++) {
				if(languages[i]['code']==lang.substr(0,2)) {
					newPath = languages[i]['path'];
					newLanguage = languages[i]['code'];
				}
			}
			if(!newPath && newPath!='') {
				for(var i = 0; i < languages.length; i++) {
					if(languages[i]['defaultLanguage']==true) {
						newPath = languages[i]['path'];
						newLanguage = languages[i]['code'];
					}
				}
			}			
			if($('html').attr('lang') == newLanguage) { return; }
			setCookieLang(newLanguage, expires);
			var newUrl = !useFullPaths ? domain + newPath : newPath;
			window.location.replace(newUrl + window.location.hash);
		}	
	}
	
	// COOKIE MANAGEMENT
	function setCookieLang(newLanguage, days) {
		if(!days) {
			var expires = '';
		} else {
			var expires = new Date();
			expires.setDate(expires.getDate() + days);
			expires = '; expires=' + expires.toUTCString();
		}
		document.cookie = 'languageDetection=' + newLanguage + expires + '; path=/';
	}
	
	function getCookieLang() {
		var siteCookie = document.cookie;
		var cookieLang = siteCookie.indexOf(' languageDetection=');
		if (cookieLang == -1)	{
	  		cookieLang = siteCookie.indexOf('languageDetection=');
		}
		if (cookieLang == -1) {
			var cookieLangValue = null;
	  	} else {
			var cookieLangStart = siteCookie.indexOf('=', cookieLang) + 1;
			var cookieLangEnd = siteCookie.indexOf(';', cookieLang);
			if (cookieLangEnd == -1) {
				cookieLangEnd = siteCookie.length;
			}
			cookieLangValue = unescape(siteCookie.substring(cookieLangStart,cookieLangEnd));
		}
		return cookieLangValue;
	}
	
	// PLUGIN	
	$.fn.languageDetection = function() {		
		var method = arguments[0];		
		if(methods[method]) {
			method = methods[method];
			arguments = Array.prototype.slice.call(arguments, 1);			
		} else if( typeof(method) == 'object' || !method ) {	 		
			method = methods.init;		
		} else {
			$.error( 'Method ' +  method + ' does not exist.' );
			return this;
		}		
		return method.apply(this, arguments);
	}	
	
})(jQuery);