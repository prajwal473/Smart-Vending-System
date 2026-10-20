(function () {
	const modal = document.getElementById("medicine-modal");
	if (!modal) {
		return;
	}

	const dialog = modal.querySelector(".modal-dialog");
	const title = document.getElementById("modal-title");
	const category = document.getElementById("modal-category");
	const price = document.getElementById("modal-price");
	const stock = document.getElementById("modal-stock");
	const expiry = document.getElementById("modal-expiry");
	const badges = document.getElementById("modal-badges");
	const rxBanner = document.getElementById("modal-rx");
	const quantityInput = document.getElementById("modal-quantity");
	const qtyMinus = document.getElementById("qty-minus");
	const qtyPlus = document.getElementById("qty-plus");
	const buyNow = document.getElementById("buy-now");
	const message = document.getElementById("modal-message");

	let lastFocus = null;
	let currentMedicineId = null;
	let maxQuantity = 1;
	let canSelectQuantity = false;
	let canAttemptBuy = false;
	let prescriptionRequired = false;

	function parseBoolean(value) {
		return String(value).toLowerCase() === "true";
	}

	function formatPrice(amount) {
		const number = Number(amount);
		if (Number.isNaN(number)) {
			return "₹0.00";
		}
		return "₹" + number.toLocaleString("en-IN", {
			minimumFractionDigits: 2,
			maximumFractionDigits: 2
		});
	}

	function addBadge(label, className) {
		const badge = document.createElement("span");
		badge.className = "badge " + className;
		badge.textContent = label;
		badges.appendChild(badge);
	}

	function clampQuantity() {
		let value = Number.parseInt(quantityInput.value, 10);
		if (Number.isNaN(value) || value < 1) {
			value = 1;
		}
		if (value > maxQuantity) {
			value = maxQuantity;
		}
		quantityInput.value = String(value);
	}

	function setPurchaseControls() {
		quantityInput.min = "1";
		quantityInput.max = String(Math.max(1, maxQuantity));
		quantityInput.disabled = !canSelectQuantity;
		qtyMinus.disabled = !canSelectQuantity;
		qtyPlus.disabled = !canSelectQuantity;
		buyNow.disabled = !canAttemptBuy;

		if (!canSelectQuantity) {
			quantityInput.value = "1";
		}
	}

	function fillModal(card) {
		const stockCount = Number.parseInt(card.dataset.stock, 10) || 0;
		const expired = parseBoolean(card.dataset.expired);
		const outOfStock = parseBoolean(card.dataset.outOfStock) || stockCount <= 0;
		prescriptionRequired = parseBoolean(card.dataset.rx);
		currentMedicineId = card.dataset.id || null;

		title.textContent = card.dataset.name || "Medicine";
		category.textContent = card.dataset.category || "Uncategorised";
		price.textContent = formatPrice(card.dataset.price);
		stock.textContent = String(stockCount);
		expiry.textContent = card.dataset.expiry || "Not available";

		badges.replaceChildren();
		if (!expired && !outOfStock) {
			addBadge("Available", "badge-available");
		}
		if (outOfStock) {
			addBadge("Out of Stock", "badge-stock");
		}
		if (expired) {
			addBadge("Expired", "badge-expired");
		}
		if (prescriptionRequired) {
			addBadge("Prescription Required", "badge-rx");
		}

		rxBanner.hidden = !prescriptionRequired;
		message.textContent = "";

		maxQuantity = Math.max(1, stockCount);
		canSelectQuantity = !expired && !outOfStock;
		canAttemptBuy = canSelectQuantity && !prescriptionRequired;
		quantityInput.value = "1";
		setPurchaseControls();
	}

	function focusableElements() {
		return dialog.querySelectorAll(
			'button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
		);
	}

	function openModal(card) {
		lastFocus = document.activeElement;
		fillModal(card);
		modal.hidden = false;
		document.body.classList.add("modal-open");
		dialog.focus();
	}

	function closeModal() {
		if (modal.hidden) {
			return;
		}
		modal.hidden = true;
		document.body.classList.remove("modal-open");
		if (lastFocus && typeof lastFocus.focus === "function") {
			lastFocus.focus();
		}
	}

	function openFromEvent(event) {
		const card = event.currentTarget.closest(".medicine-card");
		if (!card) {
			return;
		}
		event.preventDefault();
		openModal(card);
	}

	document.querySelectorAll(".medicine-card").forEach(function (card) {
		card.addEventListener("click", function (event) {
			if (event.target.closest("button") && !event.target.closest(".js-open-details")) {
				return;
			}
			openModal(card);
		});

		card.addEventListener("keydown", function (event) {
			if (event.target !== card) {
				return;
			}
			if (event.key === "Enter" || event.key === " ") {
				event.preventDefault();
				openModal(card);
			}
		});
	});

	document.querySelectorAll(".js-open-details").forEach(function (button) {
		button.addEventListener("click", openFromEvent);
	});

	modal.querySelectorAll("[data-close-modal]").forEach(function (element) {
		element.addEventListener("click", closeModal);
	});

	qtyMinus.addEventListener("click", function () {
		if (!canSelectQuantity) {
			return;
		}
		quantityInput.value = String(Number.parseInt(quantityInput.value, 10) - 1);
		clampQuantity();
	});

	qtyPlus.addEventListener("click", function () {
		if (!canSelectQuantity) {
			return;
		}
		quantityInput.value = String(Number.parseInt(quantityInput.value, 10) + 1);
		clampQuantity();
	});

	quantityInput.addEventListener("change", function () {
		if (canSelectQuantity) {
			clampQuantity();
		}
	});

	buyNow.addEventListener("click", function () {
		if (!canAttemptBuy) {
			if (prescriptionRequired) {
				message.textContent = "A prescription is required. Purchasing is not available for this item.";
			} else {
				message.textContent = "This medicine cannot be purchased because it is expired or out of stock.";
			}
			return;
		}
		clampQuantity();
		// Only the medicine id and quantity are sent. The server looks up the price, stock, expiry and
		// prescription rule itself and shows the confirmation page.
		const base = modal.dataset.purchaseUrl || "/purchase/confirm";
		message.textContent = "Opening purchase confirmation…";
		window.location.href = base
			+ "?medicineId=" + encodeURIComponent(currentMedicineId)
			+ "&quantity=" + encodeURIComponent(quantityInput.value);
	});

	document.addEventListener("keydown", function (event) {
		if (modal.hidden) {
			return;
		}
		if (event.key === "Escape") {
			event.preventDefault();
			closeModal();
			return;
		}
		if (event.key !== "Tab") {
			return;
		}

		const focusable = Array.from(focusableElements());
		if (focusable.length === 0) {
			event.preventDefault();
			dialog.focus();
			return;
		}

		const first = focusable[0];
		const last = focusable[focusable.length - 1];
		if (event.shiftKey && document.activeElement === first) {
			event.preventDefault();
			last.focus();
		} else if (!event.shiftKey && document.activeElement === last) {
			event.preventDefault();
			first.focus();
		}
	});
})();
