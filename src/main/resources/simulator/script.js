let input = "";

let currentScreen = "main";

function pressKey(key) {

    input += key;

    document.getElementById("input-display").textContent = input;
}

function eraseInput() {

    input = input.slice(0, -1);

    if (input === "") {
        document.getElementById("input-display").textContent = "_";
    } else {
        document.getElementById("input-display").textContent = input;
    }
}

function sendInput() {

    processInput();
}

function clearInput() {

    input = "";

    document.getElementById("input-display").textContent = "_";
}


function processInput() {

    // MAIN MENU
    if (currentScreen === "main") {

        if (input === "1") {

            currentScreen = "recipient";

            document.getElementById("ussd-message").innerHTML = `
                <p><strong>Send Money</strong></p>
                <p>Enter recipient number:</p>
                <p>Example: +263771234567</p>
                <br>
                <p>0. Back</p>
            `;

            clearInput();
        }

        return;
    }


    // RECIPIENT NUMBER
    if (currentScreen === "recipient") {

        if (input === "0") {

            currentScreen = "main";

            document.getElementById("ussd-message").innerHTML = `
                <p><strong>Welcome to Money Home</strong></p>
                <p>1. Send Money</p>
                <p>2. Check Transfer</p>
                <p>3. Language</p>
            `;

            clearInput();

            return;
        }

        if (input.length < 10) {

            document.getElementById("ussd-message").innerHTML = `
                <p><strong>Invalid number</strong></p>
                <p>Please enter a valid recipient number.</p>
            `;

            clearInput();

            return;
        }


        currentScreen = "amount";

        document.getElementById("ussd-message").innerHTML = `
            <p><strong>Send Money</strong></p>
            <p>Recipient:</p>
            <p>${input}</p>
            <br>
            <p>Enter amount in ZAR:</p>
        `;

        clearInput();

    }
}