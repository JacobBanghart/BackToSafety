/**
 * Likely Destinations Management Screen
 * Places the person may wander to during an emergency
 */

import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
    Alert,
    Linking,
    Modal,
    Platform,
    ScrollView,
    StyleSheet,
    TextInput,
    TouchableOpacity,
    View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { Colors, neutral, primary, semantic } from '@/constants/Colors';
import { useTheme } from '@/context/ThemeContext';
import {
    createDestination,
    deleteDestination,
    Destination,
    getDestinations,
    updateDestination,
} from '@/database/destinations';

type DestinationCategory =
  | 'water'
  | 'former_workplace'
  | 'church'
  | 'store'
  | 'restaurant'
  | 'friend_family'
  | 'walking_route'
  | 'other';
type RiskLevel = 'high' | 'medium' | 'low';

const CATEGORY_OPTIONS: { value: DestinationCategory; label: string; icon: string }[] = [
  { value: 'water', label: 'Water/Pool', icon: 'drop.fill' },
  { value: 'former_workplace', label: 'Former Work', icon: 'briefcase.fill' },
  { value: 'church', label: 'Church', icon: 'building.columns.fill' },
  { value: 'store', label: 'Store', icon: 'cart.fill' },
  { value: 'restaurant', label: 'Restaurant', icon: 'fork.knife' },
  { value: 'friend_family', label: 'Friend/Family', icon: 'person.2.fill' },
  { value: 'walking_route', label: 'Walking Route', icon: 'figure.walk' },
  { value: 'other', label: 'Other', icon: 'mappin' },
];

const RISK_OPTIONS: { value: RiskLevel; label: string; color: keyof typeof semantic }[] = [
  { value: 'high', label: 'High', color: 'error' },
  { value: 'medium', label: 'Medium', color: 'warning' },
  { value: 'low', label: 'Low', color: 'success' },
];

interface FormData {
  name: string;
  address: string;
  category: DestinationCategory;
  riskLevel: RiskLevel;
  reason: string;
  distanceFromHome: string;
  notes: string;
}

const EMPTY_FORM: FormData = {
  name: '',
  address: '',
  category: 'other',
  riskLevel: 'medium',
  reason: '',
  distanceFromHome: '',
  notes: '',
};

export default function DestinationsScreen() {
  const { colorScheme } = useTheme();
  const isDark = colorScheme === 'dark';
  const theme = Colors[colorScheme];

  const [destinations, setDestinations] = useState<Destination[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [editingDestination, setEditingDestination] = useState<Destination | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [formData, setFormData] = useState<FormData>(EMPTY_FORM);

  // Modal state
  const [modalVisible, setModalVisible] = useState(false);
  const [modalType, setModalType] = useState<'delete' | 'error' | 'validation' | null>(null);
  const [modalMessage, setModalMessage] = useState('');
  const [pendingDelete, setPendingDelete] = useState<Destination | null>(null);

  const loadDestinations = useCallback(async () => {
    try {
      const data = await getDestinations();
      setDestinations(data);
    } catch (error) {
      console.error('Failed to load destinations:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDestinations();
  }, [loadDestinations]);

  const showAlert = (type: 'error' | 'validation', message: string) => {
    if (Platform.OS === 'web') {
      setModalType(type);
      setModalMessage(message);
      setModalVisible(true);
    } else {
      Alert.alert(type === 'validation' ? 'Required' : 'Error', message);
    }
  };

  const handleSave = async () => {
    if (!formData.name.trim()) {
      showAlert('validation', 'Please enter a name for this location.');
      return;
    }

    try {
      if (editingDestination?.id) {
        await updateDestination(editingDestination.id, {
          name: formData.name,
          address: formData.address || undefined,
          category: formData.category,
          riskLevel: formData.riskLevel,
          reason: formData.reason || undefined,
          distanceFromHome: formData.distanceFromHome || undefined,
          notes: formData.notes || undefined,
        });
      } else {
        await createDestination({
          name: formData.name,
          address: formData.address || undefined,
          category: formData.category,
          riskLevel: formData.riskLevel,
          reason: formData.reason || undefined,
          distanceFromHome: formData.distanceFromHome || undefined,
          notes: formData.notes || undefined,
        });
      }

      await loadDestinations();
      setShowForm(false);
      setEditingDestination(null);
      setFormData(EMPTY_FORM);
    } catch (error) {
      console.error('Failed to save destination:', error);
      showAlert('error', 'Failed to save location. Please try again.');
    }
  };

  const handleEdit = (destination: Destination) => {
    setEditingDestination(destination);
    setFormData({
      name: destination.name,
      address: destination.address || '',
      category: destination.category || 'other',
      riskLevel: destination.riskLevel || 'medium',
      reason: destination.reason || '',
      distanceFromHome: destination.distanceFromHome || '',
      notes: destination.notes || '',
    });
    setShowForm(true);
  };

  const handleDelete = (destination: Destination) => {
    if (Platform.OS === 'web') {
      setPendingDelete(destination);
      setModalType('delete');
      setModalMessage(`Are you sure you want to remove "${destination.name}"?`);
      setModalVisible(true);
    } else {
      Alert.alert('Delete Location', `Are you sure you want to remove "${destination.name}"?`, [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: () => confirmDelete(destination),
        },
      ]);
    }
  };

  const confirmDelete = async (destination: Destination) => {
    try {
      if (destination.id) {
        await deleteDestination(destination.id);
        await loadDestinations();
      }
    } catch (error) {
      console.error('Failed to delete destination:', error);
      showAlert('error', 'Failed to delete location.');
    }
  };

  const handleModalAction = (action: 'confirm' | 'cancel') => {
    setModalVisible(false);
    if (action === 'confirm' && modalType === 'delete' && pendingDelete) {
      confirmDelete(pendingDelete);
    }
    setPendingDelete(null);
    setModalType(null);
    setModalMessage('');
  };

  const handleOpenMaps = (address: string) => {
    const encoded = encodeURIComponent(address);
    const url = Platform.select({
      ios: `maps:?q=${encoded}`,
      android: `geo:0,0?q=${encoded}`,
      default: `https://www.google.com/maps/search/?api=1&query=${encoded}`,
    });
    if (url) Linking.openURL(url);
  };

  const handleAddNew = () => {
    setEditingDestination(null);
    setFormData(EMPTY_FORM);
    setShowForm(true);
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingDestination(null);
    setFormData(EMPTY_FORM);
  };

  const getCategoryInfo = (category?: DestinationCategory) => {
    return CATEGORY_OPTIONS.find((c) => c.value === category) || CATEGORY_OPTIONS[7];
  };

  const getRiskInfo = (riskLevel?: RiskLevel) => {
    return RISK_OPTIONS.find((r) => r.value === riskLevel) || RISK_OPTIONS[1];
  };

  const renderDestinationCard = (destination: Destination) => {
    const categoryInfo = getCategoryInfo(destination.category);
    const riskInfo = getRiskInfo(destination.riskLevel);
    const riskColor = semantic[riskInfo.color];
    const isWater = destination.category === 'water';

    return (
      <View
        key={destination.id}
        style={[
          styles.card,
          {
            backgroundColor: isDark ? neutral[800] : neutral[50],
            borderColor: isDark ? neutral[700] : neutral[200],
            borderLeftColor: riskColor,
          },
        ]}
      >
        <View style={styles.cardHeader}>
          <View style={styles.cardInfo}>
            <View style={styles.nameRow}>
              <View
                style={[
                  styles.categoryIcon,
                  { backgroundColor: isWater ? `${semantic.warning}20` : `${primary[600]}15` },
                ]}
              >
                <IconSymbol
                  name={categoryInfo.icon as any}
                  size={16}
                  color={isWater ? semantic.warning : primary[600]}
                />
              </View>
              <ThemedText style={styles.destinationName} numberOfLines={1}>
                {destination.name}
              </ThemedText>
            </View>
            <View style={styles.metaRow}>
              <View style={[styles.riskBadge, { backgroundColor: `${riskColor}20` }]}>
                <ThemedText style={[styles.riskText, { color: riskColor }]}>
                  {riskInfo.label}
                </ThemedText>
              </View>
              <ThemedText
                style={styles.categoryText}
                lightColor={neutral[500]}
                darkColor={neutral[400]}
              >
                {categoryInfo.label}
              </ThemedText>
            </View>
          </View>

          <TouchableOpacity
            style={[styles.editButton, { backgroundColor: `${primary[600]}15` }]}
            onPress={() => handleEdit(destination)}
          >
            <IconSymbol name="pencil" size={14} color={primary[600]} />
          </TouchableOpacity>
        </View>

        {destination.address && (
          <TouchableOpacity
            style={[styles.addressRow, { backgroundColor: isDark ? neutral[700] : neutral[100] }]}
            onPress={() => handleOpenMaps(destination.address!)}
          >
            <IconSymbol name="location" size={14} color={primary[600]} />
            <ThemedText
              style={styles.addressText}
              lightColor={primary[700]}
              darkColor={primary[300]}
              numberOfLines={2}
            >
              {destination.address}
            </ThemedText>
            <IconSymbol name="arrow.up.right" size={12} color={primary[600]} />
          </TouchableOpacity>
        )}

        {destination.distanceFromHome && (
          <View style={styles.detailRow}>
            <IconSymbol
              name="arrow.left.arrow.right"
              size={12}
              color={isDark ? neutral[500] : neutral[400]}
            />
            <ThemedText
              style={styles.detailText}
              lightColor={neutral[600]}
              darkColor={neutral[400]}
            >
              {destination.distanceFromHome} from home
            </ThemedText>
          </View>
        )}

        {destination.reason && (
          <View style={styles.reasonBox}>
            <ThemedText
              style={styles.reasonLabel}
              lightColor={neutral[500]}
              darkColor={neutral[500]}
            >
              Why they might go here:
            </ThemedText>
            <ThemedText
              style={styles.reasonText}
              lightColor={neutral[700]}
              darkColor={neutral[300]}
            >
              {destination.reason}
            </ThemedText>
          </View>
        )}

        <TouchableOpacity style={styles.deleteButton} onPress={() => handleDelete(destination)}>
          <ThemedText style={[styles.deleteText, { color: semantic.error }]}>Remove</ThemedText>
        </TouchableOpacity>
      </View>
    );
  };

  const renderForm = () => (
    <ScrollView style={styles.formContainer} showsVerticalScrollIndicator={false}>
      <ThemedText style={styles.formTitle}>
        {editingDestination ? 'Edit Location' : 'Add Likely Destination'}
      </ThemedText>

      <ThemedText style={styles.formHint} lightColor={neutral[600]} darkColor={neutral[400]}>
        Add places your loved one may wander to. These will be checked during a search.
      </ThemedText>

      {/* Name */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Location Name *
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.name}
          onChangeText={(text) => setFormData({ ...formData, name: text })}
          placeholder="e.g., Riverside Park, Old House on Maple St"
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
        />
      </View>

      {/* Category */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Location Type
        </ThemedText>
        <View style={styles.optionGrid}>
          {CATEGORY_OPTIONS.map((option) => {
            const isWater = option.value === 'water';
            const isSelected = formData.category === option.value;
            return (
              <TouchableOpacity
                key={option.value}
                style={[
                  styles.optionButton,
                  {
                    backgroundColor: isSelected
                      ? isWater
                        ? semantic.warning
                        : primary[600]
                      : isDark
                        ? neutral[800]
                        : neutral[100],
                    borderColor: isSelected
                      ? isWater
                        ? semantic.warning
                        : primary[600]
                      : isDark
                        ? neutral[600]
                        : neutral[300],
                  },
                ]}
                onPress={() => setFormData({ ...formData, category: option.value })}
              >
                <IconSymbol
                  name={option.icon as any}
                  size={14}
                  color={isSelected ? '#fff' : neutral[500]}
                />
                <ThemedText
                  style={[styles.optionText, isSelected && { color: '#fff' }]}
                  lightColor={neutral[700]}
                  darkColor={neutral[300]}
                >
                  {option.label}
                </ThemedText>
              </TouchableOpacity>
            );
          })}
        </View>
        {formData.category === 'water' && (
          <View style={[styles.warningBox, { backgroundColor: `${semantic.warning}15` }]}>
            <IconSymbol name="exclamationmark.triangle.fill" size={16} color={semantic.warning} />
            <ThemedText style={[styles.warningText, { color: semantic.warning }]}>
              Water locations are high priority. Always check water first!
            </ThemedText>
          </View>
        )}
      </View>

      {/* Risk Level */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Search Priority
        </ThemedText>
        <View style={styles.riskRow}>
          {RISK_OPTIONS.map((option) => {
            const color = semantic[option.color];
            const isSelected = formData.riskLevel === option.value;
            return (
              <TouchableOpacity
                key={option.value}
                style={[
                  styles.riskOption,
                  {
                    backgroundColor: isSelected ? color : 'transparent',
                    borderColor: color,
                  },
                ]}
                onPress={() => setFormData({ ...formData, riskLevel: option.value })}
              >
                <ThemedText style={[styles.riskOptionText, { color: isSelected ? '#fff' : color }]}>
                  {option.label}
                </ThemedText>
              </TouchableOpacity>
            );
          })}
        </View>
      </View>

      {/* Address */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Address
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            styles.textArea,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.address}
          onChangeText={(text) => setFormData({ ...formData, address: text })}
          placeholder="Street address or description"
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
          multiline
          numberOfLines={2}
        />
      </View>

      {/* Distance */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Distance from Home
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.distanceFromHome}
          onChangeText={(text) => setFormData({ ...formData, distanceFromHome: text })}
          placeholder="e.g., 0.5 miles, 2 blocks"
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
        />
      </View>

      {/* Reason */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Why They Might Go Here
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            styles.textArea,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.reason}
          onChangeText={(text) => setFormData({ ...formData, reason: text })}
          placeholder="e.g., Lived here 30 years ago, Used to work here"
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
          multiline
          numberOfLines={3}
        />
      </View>

      {/* Notes */}
      <View style={styles.formField}>
        <ThemedText style={styles.fieldLabel} lightColor={neutral[700]} darkColor={neutral[300]}>
          Additional Notes
        </ThemedText>
        <TextInput
          style={[
            styles.textInput,
            styles.textArea,
            {
              backgroundColor: isDark ? neutral[800] : neutral[50],
              borderColor: isDark ? neutral[600] : neutral[300],
              color: isDark ? neutral[100] : neutral[900],
            },
          ]}
          value={formData.notes}
          onChangeText={(text) => setFormData({ ...formData, notes: text })}
          placeholder="Any other helpful details..."
          placeholderTextColor={isDark ? neutral[500] : neutral[400]}
          multiline
          numberOfLines={3}
        />
      </View>

      {/* Buttons */}
      <View style={styles.formButtons}>
        <TouchableOpacity
          style={[styles.cancelButton, { borderColor: neutral[400] }]}
          onPress={handleCancel}
        >
          <ThemedText
            style={styles.cancelButtonText}
            lightColor={neutral[700]}
            darkColor={neutral[300]}
          >
            Cancel
          </ThemedText>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.saveButton, { backgroundColor: primary[600] }]}
          onPress={handleSave}
        >
          <ThemedText style={styles.saveButtonText}>
            {editingDestination ? 'Update' : 'Add'}
          </ThemedText>
        </TouchableOpacity>
      </View>

      <View style={{ height: 40 }} />
    </ScrollView>
  );

  const renderDestinationList = () => (
    <ScrollView style={styles.listContainer} showsVerticalScrollIndicator={false}>
      {/* Info Box */}
      <View
        style={[styles.infoBox, { backgroundColor: isDark ? `${primary[900]}50` : primary[50] }]}
      >
        <IconSymbol name="info.circle.fill" size={18} color={primary[600]} />
        <ThemedText style={styles.infoText} lightColor={primary[800]} darkColor={primary[200]}>
          Most people with dementia are found within 1.5 miles of where they were last seen. Check
          water sources first!
        </ThemedText>
      </View>

      {destinations.length === 0 ? (
        <View style={styles.emptyState}>
          <IconSymbol
            name="mappin.and.ellipse"
            size={48}
            color={isDark ? neutral[600] : neutral[300]}
          />
          <ThemedText style={styles.emptyTitle} lightColor={neutral[700]} darkColor={neutral[300]}>
            No Destinations Added
          </ThemedText>
          <ThemedText style={styles.emptyText} lightColor={neutral[500]} darkColor={neutral[400]}>
            Add places your loved one may wander to, like former homes, favorite stores, or water
            sources.
          </ThemedText>
        </View>
      ) : (
        <>
          <View style={styles.listHeader}>
            <ThemedText style={styles.listCount} lightColor={neutral[600]} darkColor={neutral[400]}>
              {destinations.length} location{destinations.length !== 1 ? 's' : ''} • Sorted by
              priority
            </ThemedText>
          </View>
          {destinations.map(renderDestinationCard)}
        </>
      )}

      <TouchableOpacity
        style={[styles.addButton, { backgroundColor: primary[600] }]}
        onPress={handleAddNew}
      >
        <IconSymbol name="plus" size={20} color="#fff" />
        <ThemedText style={styles.addButtonText}>Add Location</ThemedText>
      </TouchableOpacity>

      <View style={{ height: 40 }} />
    </ScrollView>
  );

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.safeArea} edges={['top']}>
        {/* Header */}
        <View style={[styles.header, { borderBottomColor: isDark ? neutral[800] : neutral[200] }]}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.replace('/(tabs)')}>
            <IconSymbol name="chevron.left" size={24} color={primary[600]} />
          </TouchableOpacity>
          <ThemedText style={styles.headerTitle}>Likely Destinations</ThemedText>
          <View style={styles.headerRight} />
        </View>

        {/* Content */}
        {isLoading ? (
          <View style={styles.loading}>
            <ThemedText>Loading...</ThemedText>
          </View>
        ) : showForm ? (
          renderForm()
        ) : (
          renderDestinationList()
        )}
      </SafeAreaView>

      {/* Alert Modal for Web */}
      <Modal
        animationType="fade"
        transparent
        visible={modalVisible}
        onRequestClose={() => handleModalAction('cancel')}>
        <View style={styles.modalOverlay}>
          <View style={[styles.modalContent, { backgroundColor: theme.card }]}>
            <ThemedText style={styles.modalTitle}>
              {modalType === 'delete'
                ? 'Delete Location'
                : modalType === 'validation'
                  ? 'Required'
                  : 'Error'}
            </ThemedText>
            <ThemedText style={styles.modalText}>{modalMessage}</ThemedText>
            <View
              style={[
                styles.modalButtons,
                modalType === 'delete' ? {} : { justifyContent: 'center' },
              ]}>
              {modalType === 'delete' && (
                <TouchableOpacity
                  style={[styles.modalButton, styles.modalCancelButton, { borderColor: theme.border }]}
                  onPress={() => handleModalAction('cancel')}>
                  <ThemedText style={styles.modalButtonText}>Cancel</ThemedText>
                </TouchableOpacity>
              )}
              <TouchableOpacity
                style={[
                  styles.modalButton,
                  modalType === 'delete'
                    ? { backgroundColor: semantic.error }
                    : { backgroundColor: primary[500] },
                ]}
                onPress={() => handleModalAction('confirm')}>
                <ThemedText style={[styles.modalButtonText, { color: '#fff' }]}>
                  {modalType === 'delete' ? 'Delete' : 'OK'}
                </ThemedText>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  safeArea: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  backButton: {
    padding: 8,
    marginLeft: -8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: '600',
  },
  headerRight: {
    width: 40,
  },
  loading: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },

  // List styles
  listContainer: {
    flex: 1,
    padding: 16,
  },
  infoBox: {
    flexDirection: 'row',
    padding: 12,
    borderRadius: 10,
    gap: 10,
    marginBottom: 16,
  },
  infoText: {
    flex: 1,
    fontSize: 13,
    lineHeight: 18,
  },
  listHeader: {
    marginBottom: 12,
  },
  listCount: {
    fontSize: 14,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: 40,
    paddingHorizontal: 32,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    marginTop: 16,
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },

  // Destination card
  card: {
    borderRadius: 12,
    borderWidth: 1,
    borderLeftWidth: 4,
    padding: 16,
    marginBottom: 12,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 10,
  },
  cardInfo: {
    flex: 1,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  categoryIcon: {
    width: 32,
    height: 32,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  destinationName: {
    fontSize: 17,
    fontWeight: '600',
    flex: 1,
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginTop: 6,
    marginLeft: 42,
  },
  riskBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 4,
  },
  riskText: {
    fontSize: 11,
    fontWeight: '600',
  },
  categoryText: {
    fontSize: 13,
  },
  editButton: {
    width: 32,
    height: 32,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  addressRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingVertical: 8,
    paddingHorizontal: 10,
    borderRadius: 8,
    marginBottom: 8,
  },
  addressText: {
    fontSize: 13,
    flex: 1,
  },
  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 6,
  },
  detailText: {
    fontSize: 13,
  },
  reasonBox: {
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: 'rgba(128,128,128,0.2)',
  },
  reasonLabel: {
    fontSize: 11,
    fontWeight: '500',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 2,
  },
  reasonText: {
    fontSize: 14,
    fontStyle: 'italic',
  },
  deleteButton: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: 'rgba(128,128,128,0.2)',
    alignItems: 'center',
  },
  deleteText: {
    fontSize: 14,
    fontWeight: '500',
  },
  addButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 16,
    borderRadius: 12,
    marginTop: 8,
  },
  addButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },

  // Form styles
  formContainer: {
    flex: 1,
    padding: 16,
  },
  formTitle: {
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 8,
  },
  formHint: {
    fontSize: 14,
    marginBottom: 20,
    lineHeight: 20,
  },
  formField: {
    marginBottom: 16,
  },
  fieldLabel: {
    fontSize: 14,
    fontWeight: '500',
    marginBottom: 6,
  },
  textInput: {
    borderWidth: 1,
    borderRadius: 10,
    padding: 14,
    fontSize: 16,
  },
  textArea: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  optionGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  optionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
  },
  optionText: {
    fontSize: 13,
    fontWeight: '500',
  },
  warningBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 10,
    borderRadius: 8,
    marginTop: 8,
  },
  warningText: {
    fontSize: 12,
    flex: 1,
  },
  riskRow: {
    flexDirection: 'row',
    gap: 10,
  },
  riskOption: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 8,
    borderWidth: 2,
    alignItems: 'center',
  },
  riskOptionText: {
    fontSize: 14,
    fontWeight: '600',
  },
  formButtons: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 24,
  },
  cancelButton: {
    flex: 1,
    padding: 16,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
  },
  cancelButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
  saveButton: {
    flex: 1,
    padding: 16,
    borderRadius: 10,
    alignItems: 'center',
  },
  saveButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },

  // Modal styles
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalContent: {
    borderRadius: 16,
    padding: 24,
    width: '100%',
    maxWidth: 340,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 12,
    textAlign: 'center',
  },
  modalText: {
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 24,
    textAlign: 'center',
  },
  modalButtons: {
    flexDirection: 'row',
    gap: 12,
  },
  modalButton: {
    flex: 1,
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderRadius: 10,
    alignItems: 'center',
  },
  modalCancelButton: {
    borderWidth: 1,
  },
  modalButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
});
