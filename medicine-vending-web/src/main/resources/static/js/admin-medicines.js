(function () {
	document.querySelectorAll('form.confirm-form').forEach(function (form) {
		form.addEventListener('submit', function (e) {
			var msg = form.getAttribute('data-confirm') || 'Are you sure?';
			if (!window.confirm(msg)) { e.preventDefault(); }
		});
	});
})();
