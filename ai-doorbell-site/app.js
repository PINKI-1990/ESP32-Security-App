/* ==========================================================================
   SENTINEL - Autonomous AI Smart Doorbell
   3D WebGL Camera Rotations & Scroll Animations (Three.js + GSAP ScrollTrigger)
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {
    initPreloader();
    initCustomCursor();
    initNavigation();
    init3DScene();
    initBiometricHUD();
    initCounters();
});

/* --------------------------------------------------------------------------
   1. PRELOADER
   -------------------------------------------------------------------------- */
function initPreloader() {
    const preloader = document.getElementById('preloader');
    const bar = document.getElementById('preloader-bar');
    const percentText = document.getElementById('preloader-percent');
    const statusText = document.getElementById('preloader-status');

    const statuses = [
        "Initializing 3D Optic Engine...",
        "Loading Biometric Shaders...",
        "Calibrating Camera Matrices...",
        "Configuring Neural Nodes...",
        "SENTINEL Ready."
    ];

    let progress = 0;
    const interval = setInterval(() => {
        progress += Math.floor(Math.random() * 18) + 6;
        if (progress > 100) progress = 100;

        bar.style.width = `${progress}%`;
        percentText.textContent = `${progress}%`;
        
        const idx = Math.floor((progress / 100) * (statuses.length - 1));
        statusText.textContent = statuses[idx];

        if (progress === 100) {
            clearInterval(interval);
            setTimeout(() => {
                preloader.classList.add('loaded');
            }, 400);
        }
    }, 100);
}

/* --------------------------------------------------------------------------
   2. CUSTOM CURSOR
   -------------------------------------------------------------------------- */
function initCustomCursor() {
    const cursor = document.getElementById('cursor');
    const follower = document.getElementById('cursor-follower');

    let mouseX = 0, mouseY = 0;
    let followerX = 0, followerY = 0;

    window.addEventListener('mousemove', (e) => {
        mouseX = e.clientX;
        mouseY = e.clientY;

        cursor.style.left = `${mouseX}px`;
        cursor.style.top = `${mouseY}px`;
    });

    function loop() {
        followerX += (mouseX - followerX) * 0.15;
        followerY += (mouseY - followerY) * 0.15;

        follower.style.left = `${followerX}px`;
        follower.style.top = `${followerY}px`;

        requestAnimationFrame(loop);
    }
    requestAnimationFrame(loop);
}

/* --------------------------------------------------------------------------
   3. NAVIGATION
   -------------------------------------------------------------------------- */
function initNavigation() {
    const navbar = document.getElementById('navbar');
    window.addEventListener('scroll', () => {
        if (window.scrollY > 40) {
            navbar.classList.add('scrolled');
        } else {
            navbar.classList.remove('scrolled');
        }
    });
}

/* --------------------------------------------------------------------------
   4. THREE.JS 3D SCENE: ROTATING OBJECTS & CAMERA ROTATIONS ON SCROLL
   -------------------------------------------------------------------------- */
function init3DScene() {
    const canvas = document.getElementById('webgl-canvas');
    if (!canvas || typeof THREE === 'undefined') return;

    // SCENE, CAMERA, RENDERER
    const scene = new THREE.Scene();
    
    // Light background fog for seamless blend with white luxury layout
    scene.fog = new THREE.FogExp2(0xfcfcfd, 0.04);

    const camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 0.1, 1000);
    camera.position.set(2.8, 0.5, 6.5);

    const renderer = new THREE.WebGLRenderer({
        canvas: canvas,
        alpha: true,
        antialias: true,
        powerPreference: "high-performance"
    });
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;

    // --- 3D DOORBELL PRODUCT MODEL ---
    const doorbellGroup = new THREE.Group();

    // Body: Polished Aluminum Casing
    const bodyGeo = new THREE.CylinderGeometry(0.9, 0.9, 3.4, 64);
    const bodyMat = new THREE.MeshPhysicalMaterial({
        color: 0x1e293b,
        metalness: 0.85,
        roughness: 0.15,
        clearcoat: 0.8,
        clearcoatRoughness: 0.1
    });
    const bodyMesh = new THREE.Mesh(bodyGeo, bodyMat);
    doorbellGroup.add(bodyMesh);

    // Front Sapphire Glass Face
    const glassGeo = new THREE.BoxGeometry(1.4, 3.1, 0.15);
    const glassMat = new THREE.MeshPhysicalMaterial({
        color: 0x0f172a,
        metalness: 0.2,
        roughness: 0.05,
        transmission: 0.7,
        opacity: 0.95,
        transparent: true
    });
    const glassMesh = new THREE.Mesh(glassGeo, glassMat);
    glassMesh.position.z = 0.85;
    doorbellGroup.add(glassMesh);

    // Camera Outer Ring Light (Glowing Cyan LED)
    const ringGeo = new THREE.TorusGeometry(0.42, 0.04, 32, 100);
    const ringMat = new THREE.MeshStandardMaterial({
        color: 0x00d2ff,
        emissive: 0x0066ff,
        emissiveIntensity: 1.5,
        roughness: 0.2
    });
    const ringMesh = new THREE.Mesh(ringGeo, ringMat);
    ringMesh.position.set(0, 0.75, 0.94);
    doorbellGroup.add(ringMesh);

    // Main Camera Lens Element
    const lensGeo = new THREE.SphereGeometry(0.36, 32, 32);
    const lensMat = new THREE.MeshPhysicalMaterial({
        color: 0x020617,
        metalness: 1.0,
        roughness: 0.0,
        clearcoat: 1.0
    });
    const lensMesh = new THREE.Mesh(lensGeo, lensMat);
    lensMesh.position.set(0, 0.75, 0.88);
    doorbellGroup.add(lensMesh);

    // IR Laser Scan Beam Frustum Cone (Visible when scanning)
    const beamGeo = new THREE.ConeGeometry(1.8, 4, 32, 1, true);
    beamGeo.rotateX(-Math.PI / 2);
    const beamMat = new THREE.MeshBasicMaterial({
        color: 0x00d2ff,
        transparent: true,
        opacity: 0.12,
        side: THREE.DoubleSide
    });
    const scanBeam = new THREE.Mesh(beamGeo, beamMat);
    scanBeam.position.set(0, 0.75, 2.9);
    doorbellGroup.add(scanBeam);

    // Bottom Ring Button
    const buttonRingGeo = new THREE.TorusGeometry(0.35, 0.03, 32, 64);
    const buttonRingMat = new THREE.MeshStandardMaterial({
        color: 0x0066ff,
        emissive: 0x0066ff,
        emissiveIntensity: 0.8
    });
    const buttonRingMesh = new THREE.Mesh(buttonRingGeo, buttonRingMat);
    buttonRingMesh.position.set(0, -0.75, 0.94);
    doorbellGroup.add(buttonRingMesh);

    scene.add(doorbellGroup);

    // --- FLOATING ROTATING OBJECTS AROUND SCENE ---
    const floatingGroup = new THREE.Group();

    // 1. Floating Torus Lenses
    const torusList = [];
    for (let i = 0; i < 4; i++) {
        const tGeo = new THREE.TorusGeometry(0.8 + i * 0.4, 0.02, 16, 100);
        const tMat = new THREE.MeshStandardMaterial({
            color: 0x0066ff,
            metalness: 0.9,
            roughness: 0.1,
            transparent: true,
            opacity: 0.4 - i * 0.08
        });
        const tMesh = new THREE.Mesh(tGeo, tMat);
        tMesh.position.set((Math.random() - 0.5) * 4, (Math.random() - 0.5) * 4, (Math.random() - 0.5) * 3);
        tMesh.rotation.set(Math.random() * Math.PI, Math.random() * Math.PI, 0);
        floatingGroup.add(tMesh);
        torusList.push(tMesh);
    }

    // 2. Floating AI Node Spheres
    const nodeSpheres = [];
    const nodeGeo = new THREE.IcosahedronGeometry(0.15, 2);
    const nodeMat = new THREE.MeshPhysicalMaterial({
        color: 0x00d2ff,
        metalness: 0.3,
        roughness: 0.1,
        clearcoat: 1.0,
        emissive: 0x0066ff,
        emissiveIntensity: 0.4
    });

    for (let i = 0; i < 12; i++) {
        const node = new THREE.Mesh(nodeGeo, nodeMat);
        node.position.set(
            (Math.random() - 0.5) * 7,
            (Math.random() - 0.5) * 6,
            (Math.random() - 0.5) * 5
        );
        floatingGroup.add(node);
        nodeSpheres.push(node);
    }

    // 3. Floating Biometric Hexagon Shield
    const hexGeo = new THREE.CylinderGeometry(0.5, 0.5, 0.02, 6);
    const hexMat = new THREE.MeshStandardMaterial({
        color: 0x6366f1,
        metalness: 0.8,
        roughness: 0.2,
        wireframe: true
    });
    const hexShield = new THREE.Mesh(hexGeo, hexMat);
    hexShield.position.set(-2.5, 1.2, -1);
    floatingGroup.add(hexShield);

    scene.add(floatingGroup);

    // --- LIGHTING ---
    const ambientLight = new THREE.AmbientLight(0xffffff, 1.2);
    scene.add(ambientLight);

    const dirLight1 = new THREE.DirectionalLight(0xffffff, 1.8);
    dirLight1.position.set(5, 8, 5);
    scene.add(dirLight1);

    const pointLightBlue = new THREE.PointLight(0x0066ff, 3, 12);
    pointLightBlue.position.set(-3, 2, 4);
    scene.add(pointLightBlue);

    const pointLightCyan = new THREE.PointLight(0x00d2ff, 2, 10);
    pointLightCyan.position.set(3, -2, 3);
    scene.add(pointLightCyan);

    // --- MOUSE PARALLAX ---
    let mouseX = 0, mouseY = 0;
    window.addEventListener('mousemove', (e) => {
        mouseX = (e.clientX / window.innerWidth - 0.5) * 0.5;
        mouseY = (e.clientY / window.innerHeight - 0.5) * 0.5;
    });

    // --- RENDER & IDLE ANIMATION LOOP ---
    function animate() {
        requestAnimationFrame(animate);

        // Continuous slow spin of floating background objects
        floatingGroup.rotation.y += 0.002;
        hexShield.rotation.z += 0.005;

        torusList.forEach((t, idx) => {
            t.rotation.x += 0.001 * (idx + 1);
            t.rotation.y += 0.0015 * (idx + 1);
        });

        nodeSpheres.forEach((ns, idx) => {
            ns.position.y += Math.sin(Date.now() * 0.002 + idx) * 0.002;
        });

        // Subtle idle hover of doorbell
        doorbellGroup.position.y = Math.sin(Date.now() * 0.0015) * 0.1;

        renderer.render(scene, camera);
    }
    animate();

    // RESIZE LISTENER
    window.addEventListener('resize', () => {
        camera.aspect = window.innerWidth / window.innerHeight;
        camera.updateProjectionMatrix();
        renderer.setSize(window.innerWidth, window.innerHeight);
    });

    // --- GSAP SCROLLTRIGGER: CAMERA & OBJECT ROTATIONS ON SCROLL ---
    if (typeof gsap !== 'undefined' && typeof ScrollTrigger !== 'undefined') {
        gsap.registerPlugin(ScrollTrigger);

        // Timeline driven by page scroll
        const scrollTl = gsap.timeline({
            scrollTrigger: {
                trigger: "#main-content",
                start: "top top",
                end: "bottom bottom",
                scrub: 1.2
            }
        });

        // Step 1 -> Step 2 (Optics Section): Camera pans around side of doorbell lens & floating objects rotate 360deg
        scrollTl.to(camera.position, {
            x: 1.8,
            y: 0.75,
            z: 3.8,
            duration: 2
        }, 0);

        scrollTl.to(doorbellGroup.rotation, {
            y: Math.PI * 0.45,
            x: 0.1,
            duration: 2
        }, 0);

        scrollTl.to(floatingGroup.rotation, {
            y: Math.PI * 2,
            x: Math.PI * 0.5,
            duration: 2
        }, 0);

        // Step 2 -> Step 3 (Biometric Recognition Section): Camera rotates face-on to lens
        scrollTl.to(camera.position, {
            x: 0,
            y: 0.75,
            z: 4.5,
            duration: 2
        }, 2);

        scrollTl.to(doorbellGroup.rotation, {
            y: 0,
            x: 0,
            duration: 2
        }, 2);

        scrollTl.to(scanBeam.material, {
            opacity: 0.35,
            duration: 1
        }, 2);

        // Step 3 -> Step 4 (Mobile Section): Camera pulls back & angles slightly upward
        scrollTl.to(camera.position, {
            x: -2.2,
            y: 0.2,
            z: 5.5,
            duration: 2
        }, 4);

        scrollTl.to(doorbellGroup.rotation, {
            y: -Math.PI * 0.35,
            x: -0.1,
            duration: 2
        }, 4);

        scrollTl.to(scanBeam.material, {
            opacity: 0.05,
            duration: 1
        }, 4);

        // Step 4 -> Step 5 (Timeline Section): Full 180 camera sweep & spin floating objects
        scrollTl.to(camera.position, {
            x: 2.5,
            y: -0.4,
            z: 6.0,
            duration: 2
        }, 6);

        scrollTl.to(doorbellGroup.rotation, {
            y: Math.PI * 1.2,
            x: 0.2,
            duration: 2
        }, 6);

        scrollTl.to(floatingGroup.rotation, {
            y: Math.PI * 4,
            z: Math.PI,
            duration: 2
        }, 6);

        // Step 5 -> Step 6/7 (Specs & Pre-Order): Final Hero Macro Camera Angle
        scrollTl.to(camera.position, {
            x: 0,
            y: -0.8,
            z: 4.8,
            duration: 2
        }, 8);

        scrollTl.to(doorbellGroup.rotation, {
            y: Math.PI * 2,
            x: -0.2,
            duration: 2
        }, 8);
    }
}

/* --------------------------------------------------------------------------
   5. BIOMETRIC HUD RECOGNITION SIMULATOR & TEST SCAN BUTTONS
   -------------------------------------------------------------------------- */
function initBiometricHUD() {
    const triggerHeroBtn = document.getElementById('trigger-scan-btn');
    const triggerHudBtn = document.getElementById('hud-trigger-btn');
    
    const hudViewport = document.querySelector('.hud-viewport');
    const hudName = document.getElementById('hud-name');
    const hudScore = document.getElementById('hud-score');
    const hudKey = document.getElementById('hud-key');
    const hudStatus = document.getElementById('hud-status-text');
    const notifName = document.getElementById('notif-person-name');
    const demoNotif = document.getElementById('demo-notification');

    const visitors = [
        { name: "Sarah Johnson", score: 99.9, key: "0x8F9A...C41" },
        { name: "Alex Chen", score: 99.6, key: "0x3B2E...F19" },
        { name: "Michael Scott", score: 98.9, key: "0x7D1C...A82" },
        { name: "Emily Davis", score: 99.7, key: "0x1E4F...E90" },
        { name: "David Miller", score: 99.5, key: "0x9C3A...B77" }
    ];

    let vIdx = 0;
    let isScanningActive = false;

    function executeScan(scrollToHud = false) {
        if (isScanningActive) return;
        isScanningActive = true;

        // Smooth scroll to HUD section if requested (e.g. from Hero section button)
        if (scrollToHud) {
            const bioSection = document.getElementById('recognition');
            if (bioSection) {
                bioSection.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }

        const visitor = visitors[vIdx];
        vIdx = (vIdx + 1) % visitors.length;

        // 1. UI SCANNING STATE
        if (hudViewport) hudViewport.classList.add('is-scanning');
        if (hudStatus) hudStatus.textContent = "EVALUATING 128 EMBEDDING VECTORS...";
        if (hudName) hudName.textContent = "Scanning...";
        if (hudScore) hudScore.textContent = "0.0%";

        // Update button visual states
        [triggerHeroBtn, triggerHudBtn].forEach(btn => {
            if (btn) {
                btn.classList.add('scanning');
                const textSpan = btn.querySelector('span:not(.icon-scan):not(.icon-flash)');
                if (textSpan) textSpan.textContent = "Scanning...";
            }
        });

        // 2. COUNTER ANIMATION FOR MATCH SCORE
        let currentScore = 0;
        const scoreInterval = setInterval(() => {
            currentScore += 12.5;
            if (currentScore >= visitor.score) {
                currentScore = visitor.score;
                clearInterval(scoreInterval);
            }
            if (hudScore) hudScore.textContent = `${currentScore.toFixed(1)}%`;
        }, 60);

        // 3. MATCH COMPLETION AFTER 1.2 SECONDS
        setTimeout(() => {
            if (hudViewport) hudViewport.classList.remove('is-scanning');
            if (hudStatus) hudStatus.textContent = "NEURAL MATCH VERIFIED — ACCESS GRANTED";
            if (hudName) hudName.textContent = visitor.name;
            if (hudScore) hudScore.textContent = `${visitor.score.toFixed(1)}%`;
            if (hudKey) hudKey.textContent = visitor.key;
            if (notifName) notifName.textContent = visitor.name;

            // Trigger notification card pop animation on lockscreen mockup
            if (demoNotif && typeof gsap !== 'undefined') {
                gsap.fromTo(demoNotif,
                    { scale: 0.92, y: 15, opacity: 0.6 },
                    { scale: 1, y: 0, opacity: 1, duration: 0.5, ease: "back.out(1.8)" }
                );
            }

            // Update buttons to completion state
            [triggerHeroBtn, triggerHudBtn].forEach(btn => {
                if (btn) {
                    btn.classList.remove('scanning');
                    const textSpan = btn.querySelector('span:not(.icon-scan):not(.icon-flash)');
                    if (textSpan) textSpan.textContent = `✓ ${visitor.name}`;
                }
            });

            // Reset buttons to original text after 2.5s
            setTimeout(() => {
                if (triggerHeroBtn) {
                    const heroText = triggerHeroBtn.querySelector('span:not(.icon-scan)');
                    if (heroText) heroText.textContent = "Simulate Scan";
                }
                if (triggerHudBtn) {
                    const hudText = triggerHudBtn.querySelector('span:not(.icon-flash)');
                    if (hudText) hudText.textContent = "Run Biometric Scan";
                }
                isScanningActive = false;
            }, 2500);

        }, 1200);
    }

    // Attach Event Listeners to Buttons
    if (triggerHeroBtn) {
        triggerHeroBtn.addEventListener('click', () => executeScan(true));
    }
    if (triggerHudBtn) {
        triggerHudBtn.addEventListener('click', () => executeScan(false));
    }
}

/* --------------------------------------------------------------------------
   6. METRIC COUNTERS
   -------------------------------------------------------------------------- */
function initCounters() {
    const counters = document.querySelectorAll('.counter');
    counters.forEach(counter => {
        const target = parseFloat(counter.getAttribute('data-target'));
        let current = 0;
        const increment = target / 40;

        const updateCounter = () => {
            current += increment;
            if (current < target) {
                counter.textContent = current.toFixed(target % 1 !== 0 ? 1 : 0);
                setTimeout(updateCounter, 30);
            } else {
                counter.textContent = target;
            }
        };
        updateCounter();
    });
}
