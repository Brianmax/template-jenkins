(() => {
  'use strict';

  const deck = document.getElementById('deck');
  const slides = Array.from(document.querySelectorAll('.slide'));
  const currentLabel = document.getElementById('current-slide');
  const totalLabel = document.getElementById('total-slides');
  const progressBar = document.getElementById('progress-bar');
  const previousButton = document.getElementById('prev-slide');
  const nextButton = document.getElementById('next-slide');
  const overviewButton = document.getElementById('overview-button');
  const closeOverviewButton = document.getElementById('close-overview');
  const overview = document.getElementById('overview');
  const overviewGrid = document.getElementById('overview-grid');
  const fullscreenButton = document.getElementById('fullscreen-button');
  const helpDialog = document.getElementById('keyboard-help');
  const closeHelpButton = document.getElementById('close-help');

  let currentIndex = readIndexFromHash();
  let touchStartX = 0;
  let touchStartY = 0;

  totalLabel.textContent = String(slides.length).padStart(2, '0');

  function readIndexFromHash() {
    const match = window.location.hash.match(/^#(?:slide-)?(\d+)$/);
    if (!match) return 0;
    return Math.min(Math.max(Number(match[1]) - 1, 0), slides.length - 1);
  }

  function updateHash(index) {
    const hash = `#${index + 1}`;
    if (window.location.hash !== hash) {
      window.history.replaceState(null, '', hash);
    }
  }

  function render() {
    slides.forEach((slide, index) => {
      const active = index === currentIndex;
      slide.classList.toggle('is-active', active);
      slide.classList.toggle('is-before', index < currentIndex);
      slide.setAttribute('aria-hidden', String(!active));
      slide.inert = !active;
    });

    currentLabel.textContent = String(currentIndex + 1).padStart(2, '0');
    progressBar.style.width = `${((currentIndex + 1) / slides.length) * 100}%`;
    previousButton.disabled = currentIndex === 0;
    nextButton.disabled = currentIndex === slides.length - 1;
    document.title = `${slides[currentIndex].dataset.title} | Jenkins + GitHub`;
    updateHash(currentIndex);
    updateOverviewSelection();
  }

  function goTo(index) {
    const boundedIndex = Math.min(Math.max(index, 0), slides.length - 1);
    if (boundedIndex === currentIndex) return;
    currentIndex = boundedIndex;
    render();
  }

  function next() {
    goTo(currentIndex + 1);
  }

  function previous() {
    goTo(currentIndex - 1);
  }

  function createOverview() {
    const fragment = document.createDocumentFragment();

    slides.forEach((slide, index) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'overview-card';
      button.dataset.slideIndex = String(index);
      button.innerHTML = `<b>${String(index + 1).padStart(2, '0')}</b><span>${slide.dataset.title}</span>`;
      button.addEventListener('click', () => {
        goTo(index);
        setOverview(false);
      });
      fragment.appendChild(button);
    });

    overviewGrid.appendChild(fragment);
  }

  function updateOverviewSelection() {
    overviewGrid.querySelectorAll('.overview-card').forEach((card, index) => {
      card.classList.toggle('is-current', index === currentIndex);
      card.setAttribute('aria-current', index === currentIndex ? 'true' : 'false');
    });
  }

  function setOverview(open) {
    overview.classList.toggle('is-open', open);
    overview.setAttribute('aria-hidden', String(!open));
    overviewButton.setAttribute('aria-pressed', String(open));

    if (open) {
      const selected = overviewGrid.querySelector('.is-current');
      window.setTimeout(() => selected?.focus(), 50);
    } else {
      overviewButton.focus({ preventScroll: true });
    }
  }

  function setHelp(open) {
    helpDialog.classList.toggle('is-open', open);
    helpDialog.setAttribute('aria-hidden', String(!open));

    if (open) {
      window.setTimeout(() => closeHelpButton.focus(), 50);
    }
  }

  async function toggleFullscreen() {
    try {
      if (!document.fullscreenElement) {
        await deck.requestFullscreen();
      } else {
        await document.exitFullscreen();
      }
    } catch (error) {
      console.warn('No fue posible cambiar el modo de pantalla completa.', error);
    }
  }

  function isInteractiveTarget(target) {
    return target instanceof Element && Boolean(target.closest('a, button, input, textarea, select, [contenteditable="true"]'));
  }

  function handleKeyboard(event) {
    if (isInteractiveTarget(event.target) && event.key !== 'Escape') return;

    const overviewOpen = overview.classList.contains('is-open');
    const helpOpen = helpDialog.classList.contains('is-open');

    if (event.key === 'Escape') {
      if (helpOpen) setHelp(false);
      else if (overviewOpen) setOverview(false);
      return;
    }

    if (helpOpen) return;

    switch (event.key) {
      case 'ArrowRight':
      case 'PageDown':
        event.preventDefault();
        if (!overviewOpen) next();
        break;
      case 'ArrowLeft':
      case 'PageUp':
        event.preventDefault();
        if (!overviewOpen) previous();
        break;
      case ' ':
        event.preventDefault();
        if (!overviewOpen) next();
        break;
      case 'Home':
        event.preventDefault();
        if (!overviewOpen) goTo(0);
        break;
      case 'End':
        event.preventDefault();
        if (!overviewOpen) goTo(slides.length - 1);
        break;
      case 'o':
      case 'O':
        setOverview(!overviewOpen);
        break;
      case 'f':
      case 'F':
        toggleFullscreen();
        break;
      case '?':
        setHelp(true);
        break;
      default:
        break;
    }
  }

  function handleTouchStart(event) {
    const touch = event.changedTouches[0];
    touchStartX = touch.clientX;
    touchStartY = touch.clientY;
  }

  function handleTouchEnd(event) {
    if (overview.classList.contains('is-open') || helpDialog.classList.contains('is-open')) return;

    const touch = event.changedTouches[0];
    const deltaX = touch.clientX - touchStartX;
    const deltaY = touch.clientY - touchStartY;

    if (Math.abs(deltaX) < 55 || Math.abs(deltaX) < Math.abs(deltaY) * 1.25) return;
    if (deltaX < 0) next();
    else previous();
  }

  previousButton.addEventListener('click', previous);
  nextButton.addEventListener('click', next);
  overviewButton.addEventListener('click', () => setOverview(!overview.classList.contains('is-open')));
  closeOverviewButton.addEventListener('click', () => setOverview(false));
  fullscreenButton.addEventListener('click', toggleFullscreen);
  closeHelpButton.addEventListener('click', () => setHelp(false));

  document.addEventListener('keydown', handleKeyboard);
  document.addEventListener('fullscreenchange', () => {
    fullscreenButton.setAttribute('aria-label', document.fullscreenElement ? 'Salir de pantalla completa' : 'Pantalla completa');
  });
  window.addEventListener('hashchange', () => {
    currentIndex = readIndexFromHash();
    render();
  });
  deck.addEventListener('touchstart', handleTouchStart, { passive: true });
  deck.addEventListener('touchend', handleTouchEnd, { passive: true });

  createOverview();
  render();
})();
