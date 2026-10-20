(function () {
	var form = document.getElementById('login-form');
	var user = document.getElementById('username');
	var pass = document.getElementById('password');
	var toggle = document.getElementById('toggle-password');
	var err = document.getElementById('client-error');
	if (!form) { return; }

	form.addEventListener('submit', function (e) {
		var missing = [];
		if (!user.value.trim()) { missing.push('username'); }
		if (!pass.value) { missing.push('password'); }
		if (missing.length) {
			e.preventDefault();
			err.textContent = 'Please enter your ' + missing.join(' and ') + '.';
			err.hidden = false;
			(missing[0] === 'username' ? user : pass).focus();
		}
	});

	toggle.addEventListener('click', function () {
		var show = pass.type === 'password';
		pass.type = show ? 'text' : 'password';
		toggle.textContent = show ? 'Hide' : 'Show';
		toggle.setAttribute('aria-pressed', String(show));
	});
})();
