let input = "";

function pressKey(key) {

    input += key;

    document.getElementById("input-display").textContent = input;

    if (key === "#") {
        processInput();
    }
}

function processInput() {

    if (input === "1#") {

        document.getElementById("ussd-message").innerHTML = `
            <p><strong>Send Money</strong></p>

            <p>Enter recipient number:</p>
        `;

        input = "";

        document.getElementById("input-display").textContent = "_";
    }
}