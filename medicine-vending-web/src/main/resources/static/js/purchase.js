(function () {
	// Duplicate-submission guard (the server also enforces a one-time token; this just stops double clicks).
	var form = document.getElementById("pay-form");
	if (form) {
		var submitted = false;
		form.addEventListener("submit", function (event) {
			if (submitted) {
				event.preventDefault();
				return;
			}
			submitted = true;
			// Use a CSS class instead of "disabled": disabled buttons would not send their name/value.
			form.querySelectorAll("button").forEach(function (button) {
				button.classList.add("is-busy");
				button.setAttribute("aria-disabled", "true");
			});
			var note = document.getElementById("busy-note");
			if (note) {
				note.hidden = false;
			}
		});
	}

	var print = document.getElementById("print-receipt");
	if (print) {
		print.addEventListener("click", function () {
			window.print();
		});
	}
})();
